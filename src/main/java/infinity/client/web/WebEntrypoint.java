package infinity.client.web;

import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;

public class WebEntrypoint {
    public static void main(String[] args) {
        HTMLDocument doc = Window.current().getDocument();
        HTMLElement body = doc.getBody();
        if (body != null) {
            body.setInnerHTML("<div style='font-family:Arial,sans-serif;background:#0a0d12;color:#d9e1ee;min-height:100vh;padding:32px'>"
                    + "<h1 style='margin:0 0 12px 0;color:#a7c0ff'>Infinity Client Web Build</h1>"
                    + "<p style='margin:0 0 10px 0'>This repository currently targets the desktop LWJGL runtime.</p>"
                    + "<p style='margin:0 0 10px 0'>A full browser runtime requires Eaglercraft web platform classes that are not present in this codebase.</p>"
                    + "<p style='margin:0'>Use the desktop distribution from <code>./gradlew build</code> for gameplay.</p>"
                    + "</div>");
        }
    }
}
