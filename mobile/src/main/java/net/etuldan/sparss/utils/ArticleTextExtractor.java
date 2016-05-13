package net.etuldan.sparss.utils;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * This class is thread safe.
 *
 * @author Alex P (ifesdjeen from jreadability)
 * @author Peter Karich
 */
public class ArticleTextExtractor {
    private static final String TAG = "ArticleTextExtractor";

    // Interesting nodes
    private static final Pattern NODES = Pattern.compile("p|div|td|h1|h2|article|section|main"); //"main" is used by Joomla CMS

    // Unlikely candidates
    private static final Pattern UNLIKELY = Pattern.compile("com(bx|ment|munity)|dis(qus|cuss)|e(xtra|[-]?mail)|foot|"
            + "header|menu|re(mark|ply)|rss|sh(are|outbox)|sponsor"
            + "a(d|ll|gegate|rchive|ttachment)|(pag(er|ination))|popup|print|"
            + "login|si(debar|gn|ngle)");

    // Most likely positive candidates
    private static final Pattern POSITIVE = Pattern.compile("(^(body|content|h?entry|main|page|post|text|blog|story|haupt))"
            + "|arti(cle|kel)|instapaper_body");

    // Most likely negative candidates
    private static final Pattern NEGATIVE = Pattern.compile("nav($|igation)|user|com(ment|bx)|(^com-)|contact|"
            + "foot|masthead|(me(dia|ta))|outbrain|promo|related|scroll|(sho(utbox|pping))|"
            + "sidebar|sponsor|tags|tool|widget|player|disclaimer|toc|infobox|vcard");

    private static final Pattern NEGATIVE_STYLE =
            Pattern.compile("hidden|display: ?none|font-size: ?small");

    /**
     * @param input            extracts article text from given html string. wasn't tested
     *                         with improper HTML, although jSoup should be able to handle minor stuff.
     * @param contentIndicator a text which should be included into the extracted content, or null
     * @return extracted article, all HTML tags stripped
     */
    public static String extractContent(InputStream input, String contentIndicator, String titleIndicator) throws Exception {
        return extractContent(Jsoup.parse(input, null, ""), contentIndicator, titleIndicator);
    }

    private static String extractContent(Document doc, String contentIndicator, String titleIndicator) {
        if (doc == null)
            throw new NullPointerException("missing document");

        // now remove the clutter
        prepareDocument(doc);

        // init elements
        Collection<Element> nodes = getNodes(doc);
        int maxWeight = 0;
        Element bestMatchElement = null;

        Log.d(TAG, "======================================================");
        Log.d(TAG, "extractContent: " + titleIndicator + "");
        if(contentIndicator != null) {
            //first largest node which contains content but not title. that is the content we want.
            for (Element entry : nodes) {
                String text = entry.text();
                if(text.contains(contentIndicator)) {
                    if(!text.contains(titleIndicator)) {
                        if(maxWeight < entry.text().length()) { //use whole content length here!
                            maxWeight = entry.text().length();
                            bestMatchElement = entry;
                        }
                    }
                }
            }
        }
        if(bestMatchElement != null) {
            Log.d(TAG, "extractContent: new method worked. " + bestMatchElement.text().length());
        }

        if(bestMatchElement == null) {
            if(contentIndicator != null) {
                bestMatchElement = conventionalMatching(nodes, contentIndicator, true);
                if(bestMatchElement != null) {
                    Log.d(TAG, "extractContent: conventionalMatching worked, withContentFilter==true " + bestMatchElement.text().length());
                }
            }
            if (bestMatchElement == null) {
                bestMatchElement = conventionalMatching(nodes, contentIndicator, false);
                if(bestMatchElement != null) {
                    Log.d(TAG, "extractContent: conventionalMatching worked, withContentFilter==false " + bestMatchElement.text().length());
                }
            }
        }

        if (bestMatchElement == null) {
            Log.e(TAG, "extractContent failed. Returning document body.");
            return doc.select("body").first().toString();
        }

        Log.d(TAG, "extractContent: -----------------------------------------------------");
        Log.d(TAG, bestMatchElement.text());
        Log.d(TAG, "extractContent: -----------------------------------------------------");

        //remove child "aside" if available.
        Element aside = bestMatchElement.select("aside").first();
        if(aside != null) {
            aside.remove();
            Log.d(TAG, "extractContent: removed aside");
        }

        //check siblings for images and add them if any available
        Element previousSibling = bestMatchElement.previousElementSibling();
        while(previousSibling != null) {
            if (previousSibling.select("img").size() != 0) {
                bestMatchElement.prependChild(previousSibling);
                Log.d(TAG, "extractContent: prepended image " + previousSibling);
                previousSibling = bestMatchElement.previousElementSibling();
            } else {
                previousSibling = previousSibling.previousElementSibling();
            }
        }
        Element nextSibling = bestMatchElement.nextElementSibling();
        while(nextSibling != null) {
            if (nextSibling.select("img").size() != 0) {
                bestMatchElement.appendChild(nextSibling);
                Log.d(TAG, "extractContent: appended image " + nextSibling);
                nextSibling = bestMatchElement.nextElementSibling();
            } else {
                nextSibling = nextSibling.nextElementSibling();
            }
        }

        //search for video tags and fix them if necessary
//        IF VIDEO TAG LOOKS LIKE THIS:
//        <video style="position: absolute; top: 0px; display: none; width: 100%; padding-top: 56.25%;">
//        ...
//        <meta itemprop="thumbnailUrl" content="http://...">
//        <meta itemprop="contentURL" content="http://...">
//        </video>
//        TRANSFORM TO THIS:
//        <video controls poster="http://...">
//        <source src="http://...">
//        </video>
        for (Element video : bestMatchElement.getElementsByTag("video")) {
            String thumb = null;
            String url = null;
            for (Element meta : video.getElementsByTag("meta")) {
                if(meta.attr("itemprop").equals("thumbnailUrl")) {
                    thumb = meta.attr("content");
                }
                if(meta.attr("itemprop").equals("contentURL")) {
                    url = meta.attr("content");
                }
            }
            if(thumb != null && url != null) {
                video.attr("controls", true);
                video.attr("poster", thumb);
                video.appendElement("source").attr("src", url);
                Log.d(TAG, "extractContent: fixed video " + url);
            }
        }

        //search for img and remove lazy-loading
//        IF VIDEO TAG LOOKS LIKE THIS:
//        <figure class="NewsArticle__ChapterImage LazyImage mt-sm" data-lazy-image="{&quot;src&quot;: &quot;/ii/4/5/4/7/2/9/8/8/d51292db9620e5ed.jpeg&quot; }" data-lazy-image-text="Bild lädt...">
//        <img src="data:image/svg+xml;charset=utf-8,%3Csvg xmlns%3D'http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg' viewBox%3D'0 0 4 3'%2F%3E">
//        ...
//        </figure>
//        TRANSFORM TO THIS:
//        <img src="/ii/4/5/4/7/2/9/8/8/d51292db9620e5ed.jpeg">
        for (Element img : bestMatchElement.getElementsByTag("img")) {
            String src = null;
            if(img.parent() != null && img.parent().tag().getName().equals("figure")) {
                Element parent = img.parent();
                
                    String json = parent.attr("data-lazy-image");
//                    JSONObject obj = new JSONObject(json); //does not work.
//                    src = obj.getString("src");            //WHY?
                if(json.substring(2, 5).equals("src")) {
                    json = json.substring(6);//remove "src"
                    int first = json.indexOf("\"") + 1;
                    int last = json.indexOf("\"", first);
                    src = json.substring(first, last);
                }
            }
            if(src != null) {
                img.attr("src", src);
                Log.d(TAG, "extractContent: removed lazy-load " + src);
            }
        }

        boolean debug  = true;
        if(debug)
            return bestMatchElement.toString();

        return bestMatchElement.toString();
    }

    /**
     * Conventional matching algorithm. 
     * @param nodes All HTML elements to be considered.
     * @param contentIndicator Only required if withContentFilter==true
     * @param withContentFilter If true only nodes containing contentIndicator are considered
     * @return Best matching node or null
     */
    private static Element conventionalMatching(Collection<Element> nodes, String contentIndicator, boolean withContentFilter) {
        int maxWeight = 0;
        Element bestMatchElement = null;
        for (Element entry : nodes) {
            String text = entry.text();
            text = text.substring(0, Math.min(200, text.length())).replaceAll("[\\s\\u00A0]+"," "); //normalized beginning of text
            //only consider entries which contain the contentIndicator if withContentFilter 
            if (withContentFilter && !text.contains(contentIndicator)) {
                continue;
            }
            int currentWeight = getWeight(entry, contentIndicator);
            if (currentWeight > maxWeight) {
                maxWeight = currentWeight;
                bestMatchElement = entry;

                if (maxWeight > 300) {
                    break;
                }
            }
        }
        return bestMatchElement;
    }

    /**
     * Weights current element. By matching it with positive candidates and
     * weighting child nodes. Since it's impossible to predict which exactly
     * names, ids or class names will be used in HTML, major role is played by
     * child nodes
     *
     * @param e                Element to weight, along with child nodes
     * @param contentIndicator a text which should be included into the extracted content, or null
     */
    private static int getWeight(Element e, String contentIndicator) {
        int weight = calcWeight(e);
        weight += (int) Math.round(e.ownText().length() / 100.0 * 10);
        weight += weightChildNodes(e, contentIndicator);
        return weight;
    }

    /**
     * Weights a child nodes of given Element. During tests some difficulties
     * were met. For instance, not every single document has nested paragraph
     * tags inside of the major article tag. Sometimes people are adding one
     * more nesting level. So, we're adding 4 points for every 100 symbols
     * contained in tag nested inside of the current weighted element, but only
     * 3 points for every element that's nested 2 levels deep. This way we give
     * more chances to extract the element that has less nested levels,
     * increasing probability of the correct extraction.
     *
     * @param rootEl           Element, who's child nodes will be weighted
     * @param contentIndicator a text which should be included into the extracted content, or null
     */
    private static int weightChildNodes(Element rootEl, String contentIndicator) {
        int weight = 0;
        Element caption = null;
        List<Element> pEls = new ArrayList<>(5);
        for (Element child : rootEl.children()) {
            //if child contains only (!) a single child, get that sub-child instead (recursively!)
            while(child.children().size() == 1 && child.text().length() == 0) {
                child = child.child(0);
            }
            String text = child.text();
            int textLength = text.length();
            if (textLength < 20) {
                continue;
            }

            //this is not reliable. there are many tags (tree hierarchy) which contain contentIndicator,
            //at this point we cannot be certain that this is the tag we actually want.
            //if (contentIndicator != null && text.contains(contentIndicator)) {
            //    weight += 100; // We certainly found the item
            //}

            String ownText = child.ownText();
            int ownTextLength = ownText.length();
            if (ownTextLength > 200) {
                weight += Math.max(50, ownTextLength / 10);
            }

            if (child.tagName().equals("h1") || child.tagName().equals("h2")) {
                weight += 30;
            } else if (child.tagName().equals("div") || child.tagName().equals("p")) {
                weight += calcWeightForChild(ownText);
                if (child.tagName().equals("p") && textLength > 50)
                    pEls.add(child);

                if (child.className().toLowerCase().equals("caption"))
                    caption = child;
            }
        }

        // use caption and image
        if (caption != null)
            weight += 30;

        if (pEls.size() >= 2) {
            for (Element subEl : rootEl.children()) {
                if ("h1;h2;h3;h4;h5;h6".contains(subEl.tagName())) {
                    weight += 20;
                    // headerEls.add(subEl);
                }
            }
        }
        return weight;
    }

    private static int calcWeightForChild(String text) {
        return text.length() / 25;
//		return Math.min(100, text.length() / ((child.getAllElements().size()+1)*5));
    }

    private static int calcWeight(Element e) {
        int weight = 0;
        if (POSITIVE.matcher(e.className()).find())
            weight += 35;

        if (POSITIVE.matcher(e.id()).find())
            weight += 40;

        //also allow custom HTML attributes, e.g. like Joomla uses: itemprop="articleBody"
        if (POSITIVE.matcher(e.attributes().toString()).find())
            weight += 35;

        if (UNLIKELY.matcher(e.className()).find())
            weight -= 20;

        if (UNLIKELY.matcher(e.id()).find())
            weight -= 20;

        if (NEGATIVE.matcher(e.className()).find())
            weight -= 50;

        if (NEGATIVE.matcher(e.id()).find())
            weight -= 50;

        String style = e.attr("style");
        if (style != null && !style.isEmpty() && NEGATIVE_STYLE.matcher(style).find())
            weight -= 50;
        return weight;
    }

    /**
     * Prepares document. Currently only stipping unlikely candidates, since
     * from time to time they're getting more score than good ones especially in
     * cases when major text is short.
     *
     * @param doc document to prepare. Passed as reference, and changed inside
     *            of function
     */
    private static void prepareDocument(Document doc) {
        // stripUnlikelyCandidates(doc);
        removeScriptsAndStyles(doc);
    }

    /**
     * Removes unlikely candidates from HTML. Currently takes id and class name
     * and matches them against list of patterns
     *
     * @param doc document to strip unlikely candidates from
     */
//    protected void stripUnlikelyCandidates(Document doc) {
//        for (Element child : doc.select("body").select("*")) {
//            String className = child.className().toLowerCase();
//            String id = child.id().toLowerCase();
//
//            if (NEGATIVE.matcher(className).find()
//                    || NEGATIVE.matcher(id).find()) {
//                child.remove();
//            }
//        }
//    }
    private static Document removeScriptsAndStyles(Document doc) {
        Elements scripts = doc.getElementsByTag("script");
        for (Element item : scripts) {
            item.remove();
        }

        Elements noscripts = doc.getElementsByTag("noscript");
        for (Element item : noscripts) {
            item.remove();
        }

        Elements styles = doc.getElementsByTag("style");
        for (Element style : styles) {
            style.remove();
        }

        return doc;
    }

    /**
     * @return a set of all important nodes
     */
    private static Collection<Element> getNodes(Document doc) {
        Collection<Element> nodes = new HashSet<>(64);
        for (Element el : doc.select("body").select("*")) {
            if (NODES.matcher(el.tagName()).matches()) {
                nodes.add(el);
            }
        }
        return nodes;
    }
}
