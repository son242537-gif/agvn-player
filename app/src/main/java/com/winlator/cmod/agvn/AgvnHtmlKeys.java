/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

/**
 * JavaScript that gives an HTML game the on-screen keys ({@link AgvnLightActions}) and typed letters
 * ({@link AgvnHtmlTyping}): KeyboardEvents with keyCode forced (the constructor leaves it 0, and RPG Maker MV/MZ and
 * Tyrano read it), mouse clicks and the wheel at the screen's centre, and the HUD's frame counter. Pure Java.
 */
final class AgvnHtmlKeys {
    /**
     * Frames per second since the last call (requestAnimationFrame, which RPG Maker MV/MZ and Tyrano draw on); the
     * first call starts the counter and gives -1.
     */
    static final String FPS = "(function(){var o=window.__agvnFps;if(!o){o=window.__agvnFps={n:0,t:performance.now()};"
            + "(function f(){o.n++;requestAnimationFrame(f);})();return -1;}var now=performance.now(),"
            + "v=Math.round(o.n*1000/Math.max(1,now-o.t));o.n=0;o.t=now;return v;})()";

    /**
     * Where the player last touched or moved the mouse on the page (window.__agvnPointer), for the right button of an
     * RPG Maker game, which aims at TouchInput.x/y ({@link #rightButton}). Run once the page has loaded.
     */
    static final String TRACK = "(function(){if(window.__agvnTrack)return;window.__agvnTrack=1;"
            + "function at(x,y){window.__agvnPointer={x:x,y:y};}"
            + "function touch(e){var t=e.changedTouches&&e.changedTouches[0];if(t)at(t.clientX,t.clientY);}"
            + "function mouse(e){at(e.clientX,e.clientY);}var o={capture:true,passive:true};"
            + "document.addEventListener('touchstart',touch,o);document.addEventListener('touchmove',touch,o);"
            + "document.addEventListener('mousemove',mouse,o);document.addEventListener('mousedown',mouse,o);})();";

    private AgvnHtmlKeys() {}

    /** keydown or keyup of {key, code, keyCode} ({@link AgvnLightActions#dom}) on the focused element; it bubbles up. */
    static String press(String[] dom, boolean down) {
        return event(down ? "keydown" : "keyup", dom[0], dom[1], dom[2]);
    }

    /** A typed character: keydown, keypress (keyCode = its char code) and keyup. */
    static String typed(String ch, String code, int keyCode) {
        int charCode = ch.codePointAt(0);
        return event("keydown", ch, code, String.valueOf(keyCode)) + event("keypress", ch, code, String.valueOf(charCode))
                + event("keyup", ch, code, String.valueOf(keyCode));
    }

    private static String event(String type, String key, String code, String keyCode) {
        return "(function(){var e=new KeyboardEvent('" + type + "',{key:" + js(key) + ",code:" + js(code)
                + ",bubbles:true,cancelable:true}),n=" + Integer.parseInt(keyCode) + ";"
                + "Object.defineProperty(e,'keyCode',{get:function(){return n}});"
                + "Object.defineProperty(e,'which',{get:function(){return n}});"
                + "Object.defineProperty(e,'charCode',{get:function(){return e.type==='keypress'?n:0}});"
                + "(document.activeElement||document.body||document).dispatchEvent(e);})();";
    }

    /**
     * The right mouse button pressed ({@code down}) or let go, held as long as its key is ({@link AgvnHtmlRightButton}).
     * On an RPG Maker page (TouchInput) it goes where the player last touched ({@link #TRACK}), unless that is no part
     * of the game (the page's own background); elsewhere at the centre, as the click it was. contextmenu on release.
     */
    static String rightButton(boolean down) {
        String events = down ? "m('mousedown',2,2);" : "m('mouseup',2,0);m('contextmenu',2,0);";
        return "(function(){var p=window.TouchInput&&window.__agvnPointer,x=innerWidth/2,y=innerHeight/2,"
                + "t=p&&document.elementFromPoint(p.x,p.y);"
                + "if(t&&t!==document.body&&t!==document.documentElement){x=p.x;y=p.y;}"
                + "else t=document.elementFromPoint(x,y);t=t||document.body;"
                + "function m(n,b,bs){t.dispatchEvent(new MouseEvent(n,{bubbles:true,cancelable:true,view:window,"
                + "clientX:x,clientY:y,button:b,buttons:bs}));}" + events + "})();";
    }

    /** A mouse action of {@link AgvnLightActions} (click or wheel) at the centre of the page; "" for anything else. */
    static String mouse(int action) {
        String body;
        switch (action) {
            case AgvnLightActions.LEFT_CLICK: body = "m('mousedown',0,1);m('mouseup',0,0);m('click',0,0);"; break;
            case AgvnLightActions.MIDDLE_CLICK: body = "m('mousedown',1,4);m('mouseup',1,0);"; break;
            case AgvnLightActions.RIGHT_CLICK: body = "m('mousedown',2,2);m('mouseup',2,0);m('contextmenu',2,0);"; break;
            case AgvnLightActions.SCROLL_UP: body = wheel(-120); break;
            case AgvnLightActions.SCROLL_DOWN: body = wheel(120); break;
            default: return "";
        }
        return "(function(){var x=innerWidth/2,y=innerHeight/2,t=document.elementFromPoint(x,y)||document.body;"
                + "function m(n,b,bs){t.dispatchEvent(new MouseEvent(n,{bubbles:true,cancelable:true,view:window,"
                + "clientX:x,clientY:y,button:b,buttons:bs}));}" + body + "})();";
    }

    private static String wheel(int deltaY) {
        return "t.dispatchEvent(new WheelEvent('wheel',{bubbles:true,cancelable:true,view:window,clientX:x,clientY:y,"
                + "deltaY:" + deltaY + ",deltaMode:0}));";
    }

    /** A JavaScript string literal of {@code s}. */
    static String js(String s) {
        StringBuilder sb = new StringBuilder("'");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'' || c == '\\') sb.append('\\').append(c);
            else if (c < 0x20 || c == '<' || c == 0x2028 || c == 0x2029) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        return sb.append('\'').toString();
    }
}
