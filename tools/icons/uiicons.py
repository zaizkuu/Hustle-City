#!/usr/bin/env python3
"""Generates UiIcons.kt and UiIcons2.kt: single-color UI glyphs. Run: python3 tools/icons/uiicons.py"""
import math, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from shapes import G, icon, kotlin_file, circle, ellipse, rect, poly, line, star, gear, f

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'app/src/main/java/com/hustlecity/game/ui/icons')


def wheels(*xs, y=16.6, r=2.1):
    return G(''.join(circle(x, y, r) for x in xs))


def cloud(x=0, y=0, s=1.0):
    k = lambda v: f(v * s)
    return (f'M{f(x + 6.5 * s)} {f(y + 19 * s)}a{k(4.5)} {k(4.5)} 0 0 1 {k(-.7)} {k(-8.95)}a{k(6)} {k(6)} 0 0 1 {k(11.5)} {k(-1.45)}'
            f'a{k(5.2)} {k(5.2)} 0 1 1 {k(.2)} {k(10.4)}z')


ui = []
ui.append(icon('energy', G('M13.5 2 5 13.5h6L9.5 22 19 9.5h-6.2z')))
ui.append(icon('hunger',
    G(rect(5, 2.6, 1.2, 6.4, .6) + rect(7.4, 2.6, 1.2, 6.4, .6) + rect(9.8, 2.6, 1.2, 6.4, .6)),
    G(rect(5, 7.6, 6, 3, 1.4)), G(rect(7.2, 9.6, 1.6, 11.8, .8)),
    G('M15 2.6c2.4 1 3.6 4.4 3.6 8.4H15z'), G(rect(15, 10.4, 2, 11, 1))))
ui.append(icon('happiness', G(circle(12, 12, 9.6) + circle(8.9, 9.6, 1.4) + circle(15.1, 9.6, 1.4) + 'M7.4 13.4a4.6 4.6 0 0 0 9.2 0z', evenodd=True)))
ui.append(icon('money', G(rect(2, 5.6, 20, 12.8, 1.8) + circle(12, 12, 3.2) + circle(5.4, 12, 1.1) + circle(18.6, 12, 1.1), evenodd=True), G(circle(12, 12, 1.7))))
ui.append(icon('wallet', G(rect(2.6, 5.4, 18.8, 14.4, 2.4) + rect(13.6, 9.8, 8, 5.6, 1.8), evenodd=True), G(circle(16.6, 12.6, 1.2)), G(rect(4, 3.4, 13, 3, 1.2), alpha=.6)))
ui.append(icon('calendar', G(rect(3, 4.6, 18, 16.8, 2.2) + rect(5, 9.6, 14, 9.8, .6), evenodd=True),
    G(rect(7, 2.4, 2.2, 4.6, 1.1) + rect(14.8, 2.4, 2.2, 4.6, 1.1)),
    G(rect(6.6, 11.4, 2.6, 2.2, .4) + rect(10.7, 11.4, 2.6, 2.2, .4) + rect(14.8, 11.4, 2.6, 2.2, .4) + rect(6.6, 15.2, 2.6, 2.2, .4) + rect(10.7, 15.2, 2.6, 2.2, .4))))
ui.append(icon('clock', G(circle(12, 12, 9.8) + circle(12, 12, 7.6), evenodd=True), G(line((12, 12), (12, 6.6)) + line((12, 12), (15.6, 14.2)), stroke=2)))
ui.append(icon('pause', G(rect(5.6, 4, 4.4, 16, 1.2) + rect(14, 4, 4.4, 16, 1.2))))
ui.append(icon('play', G('M7 4.6c0-1 1-1.6 1.8-1.1l11 7.4c.8.5.8 1.7 0 2.2l-11 7.4c-.8.5-1.8-.1-1.8-1.1z')))
ui.append(icon('fast_forward', G(poly((2.6, 5.4), (12, 12), (2.6, 18.6)) + poly((12, 5.4), (21.4, 12), (12, 18.6)))))
ui.append(icon('bell', G('M12 2.6a1.6 1.6 0 0 1 1.6 1.6v.6A6.2 6.2 0 0 1 18.2 11v4.2l2.1 2.4v1.2H3.7v-1.2l2.1-2.4V11a6.2 6.2 0 0 1 4.6-6.2v-.6A1.6 1.6 0 0 1 12 2.6z'), G(circle(12, 20.6, 1.9))))
ui.append(icon('help', G(circle(12, 12, 9.6), stroke=2.2), G('M9.2 9.4a2.8 2.8 0 1 1 3.9 2.6c-.8.4-1.1 1-1.1 1.8v.6', stroke=2.3), G(circle(12, 17.4, 1.4))))
ui.append(icon('settings', G(gear(12, 12, 10.2, 7.6, 8) + circle(12, 12, 3.3), evenodd=True)))
ui.append(icon('phone', G(rect(5.8, 1.8, 12.4, 20.4, 2.6) + rect(7.6, 4.4, 8.8, 13.6, .6) + circle(12, 20, .9), evenodd=True)))
ui.append(icon('objectives', G(rect(4, 4, 16, 18, 2.2) + rect(6.6, 8.4, 2.6, 2.6, .5) + rect(10.6, 9, 6.8, 1.5, .7) + rect(6.6, 12.6, 2.6, 2.6, .5)
                                + rect(10.6, 13.2, 6.8, 1.5, .7) + rect(6.6, 16.8, 2.6, 2.6, .5) + rect(10.6, 17.4, 4.6, 1.5, .7), evenodd=True),
    G(rect(8.4, 2.2, 7.2, 3.6, 1.2))))
ui.append(icon('app_persona', G(star(12, 12.6, 10, 4.4))))
ui.append(icon('app_contacts', G(rect(2.6, 3.6, 18.8, 13.6, 3.2) + rect(6.6, 7.6, 10.8, 1.8, .9) + rect(6.6, 11.4, 7, 1.8, .9), evenodd=True),
    G(poly((6.6, 16), (6.4, 21.4), (12, 16.6)))))
ui.append(icon('app_rivals', G(circle(5, 8.6, 2.4) + circle(19, 8.6, 2.4) + 'M.8 19a4.2 4.2 0 0 1 8.4 0zM14.8 19a4.2 4.2 0 0 1 8.4 0z', alpha=.65),
    G(circle(12, 7.4, 3.3) + 'M5.4 20.4a6.6 6.2 0 0 1 13.2 0z')))
ui.append(icon('app_staff', G(rect(4, 2.6, 16, 19.4, 2.4) + rect(10, 4.4, 4, 1.6, .8) + circle(12, 10.6, 2.8) + 'M7.2 18.4a4.8 4.4 0 0 1 9.6 0z', evenodd=True)))
ui.append(icon('app_bizhub', G('M2.6 4.6h18.8v4a2.35 2.35 0 0 1-4.7 0a2.35 2.35 0 0 1-4.7 0a2.35 2.35 0 0 1-4.7 0a2.35 2.35 0 0 1-4.7 0z'),
    G(rect(4.2, 11.6, 15.6, 9.6, 1) + rect(10, 14.4, 4, 6.8) + rect(5.8, 13.6, 2.8, 3, .3) + rect(15.4, 13.6, 2.8, 3, .3), evenodd=True)))
ui.append(icon('app_finance', G(line((3, 18.4), (9, 12.4), (13, 15.4), (19, 8.4)), stroke=2.6), G(poly((21, 5.6), (14.8, 6.6), (20, 11.8)))))
ui.append(icon('app_realty', G(poly((2.6, 11.6), (12, 3.2), (21.4, 11.6), (19, 11.6), (19, 21), (5, 21), (5, 11.6)) + circle(12, 13.6, 2.1)
                                + poly((11.1, 14.6), (12.9, 14.6), (13.4, 18.4), (10.6, 18.4)), evenodd=True)))
ui.append(icon('app_fleet', G(rect(1.6, 5.2, 13.6, 10.6, 1.4) + 'M16.2 8.6h3.2l2.8 4v3.2h-6z' + rect(17, 9.6, 2.4, 2.6, .3), evenodd=True), wheels(5.6, 18.6, y=17.2, r=2.2)))
ui.append(icon('app_markets', G(rect(3.4, 9, 3.2, 7.6, .4) + rect(10.4, 5.4, 3.2, 9, .4) + rect(17.4, 7.6, 3.2, 6.2, .4)),
    G(line((5, 5.6), (5, 19.8)) + line((12, 2.6), (12, 17.6)) + line((19, 4.6), (19, 16.4)), stroke=1.4), G(line((2.4, 21.4), (21.6, 21.4)), stroke=1.6)))
ui.append(icon('app_maps', G(poly((2.6, 6), (8, 3.8), (8, 18.2), (2.6, 20.4)) + poly((16, 5.8), (21.4, 3.6), (21.4, 18), (16, 20.2))),
    G(poly((8.6, 3.8), (15.4, 5.8), (15.4, 20.2), (8.6, 18.2)), alpha=.65)))
car_body = ('M2.4 15.4v-3c0-1 .7-1.8 1.7-2l2.6-.5 2.6-3.2c.5-.6 1.2-.9 2-.9h4.3c.8 0 1.5.4 2 1l2.4 3.1 1.8.4c1 .2 1.6 1.1 1.6 2.1v3h-2.2'
            'a2.8 2.8 0 0 0-5.4 0H9.9a2.8 2.8 0 0 0-5.4 0z')
car_windows = poly((10.4, 8.2), (12.6, 8.2), (12.6, 10.2), (8.7, 10.2)) + poly((13.8, 8.2), (15.6, 8.2), (17.2, 10.2), (13.8, 10.2))
ui.append(icon('car', G(car_body + car_windows, evenodd=True), wheels(7.2, 17.8)))
ui.append(icon('van', G(rect(1.8, 5.4, 15, 10.6, 1.4) + 'M17.4 8.6h2.8l2.2 3.6v3.8h-5z' + poly((18.2, 9.6), (19.8, 9.6), (21.2, 12), (18.2, 12)), evenodd=True), wheels(6, 18.2)))
ui.append(icon('truck', G(rect(1.6, 3.6, 13.2, 12.4, 1) + 'M15.4 7.6h4.2l2.8 3.6v4.8h-7z' + poly((16.4, 8.6), (19.2, 8.6), (21, 11.2), (16.4, 11.2)), evenodd=True), wheels(5.4, 18.4)))
ui.append(icon('taxi', G(car_body + car_windows, evenodd=True), wheels(7.2, 17.8), G(rect(10.2, 3.2, 4.4, 2, .6))))
ui.append(icon('fuel', G(rect(4.4, 2.6, 10.4, 17.4, 1.6) + rect(6.4, 4.6, 6.4, 4.8, .6), evenodd=True), G(rect(3.4, 19.4, 12.4, 2.6, .8)),
    G('M14.8 8.8h2.2a1.3 1.3 0 0 1 1.3 1.3v6.2a1.5 1.5 0 0 0 3 0V8.4l-2-2.6', stroke=1.6)))
ui.append(icon('condition', G('M14.7 2.8a5.2 5.2 0 0 0-5.5 6.8l-6 6a2.1 2.1 0 0 0 3 3l6-6a5.2 5.2 0 0 0 6.8-5.5l-3.2 3.1-2.7-.6-.6-2.7z')))
ui.append(icon('parking_p', G(rect(2.6, 2.6, 18.8, 18.8, 4.4) + 'M8.6 6.6h4.6a3.8 3.8 0 0 1 0 7.6H11v3.2H8.6z' + 'M11 8.8v3.2h2.1a1.6 1.6 0 0 0 0-3.2z', evenodd=True)))
ui.append(icon('steering_wheel', G(circle(12, 12, 9.8) + circle(12, 12, 7.4), evenodd=True), G(circle(12, 12, 2.8)),
    G(rect(3.4, 10.9, 6.4, 2.2) + rect(14.2, 10.9, 6.4, 2.2) + rect(10.9, 13.8, 2.2, 6.4))))
ui.append(icon('cargo_boxes', G(rect(2.6, 12.2, 8.8, 8.8, 1) + rect(5.8, 12.2, 2.4, 3.2) + rect(12.6, 12.2, 8.8, 8.8, 1) + rect(15.8, 12.2, 2.4, 3.2)
                                + rect(7.6, 2.8, 8.8, 8.8, 1) + rect(10.8, 2.8, 2.4, 3.2), evenodd=True)))
ui.append(icon('hand_truck', G(line((5.6, 2.6), (7.6, 2.6), (7.6, 18.6), (19, 18.6)), stroke=2), G(circle(8.6, 20.4, 2.1)),
    G(rect(9.6, 8.6, 9, 8.6, 1) + rect(13, 8.6, 2.2, 3), evenodd=True)))
ui.append(icon('hands', G(rect(7, 3, 10, 8.6, 1) + rect(11, 3, 2, 3), evenodd=True),
    G('M1.8 13.4c2.6 0 4.8 1 6.4 2.6h7.6c1.6-1.6 3.8-2.6 6.4-2.6v4.6c-2.2 0-3.8.8-5.2 2.2H7c-1.4-1.4-3-2.2-5.2-2.2z')))
ui.append(icon('navigate', G(poly((12, 2.2), (20.4, 21.4), (12, 16.8), (3.6, 21.4)))))
ui.append(icon('turn_left', G('M17 21.4v-8.2a4.4 4.4 0 0 0-4.4-4.4H7', stroke=2.8), G(poly((2.4, 8.8), (8.6, 3.6), (8.6, 14)))))
ui.append(icon('turn_right', G('M7 21.4v-8.2a4.4 4.4 0 0 1 4.4-4.4H17', stroke=2.8), G(poly((21.6, 8.8), (15.4, 3.6), (15.4, 14)))))
ui.append(icon('straight', G(line((12, 21.4), (12, 8)), stroke=2.8), G(poly((12, 2.2), (17.6, 9), (6.4, 9)))))
ui.append(icon('uturn', G('M8 21.4V9.8a4 4 0 0 1 8 0v3.8', stroke=2.8), G(poly((16, 20.2), (10.8, 13.4), (21.2, 13.4)))))
ui.append(icon('arrive', G(rect(3.6, 2.4, 2, 19.2, .8)),
    G(rect(5.6, 3.4, 15, 10) + rect(5.6, 3.4, 3.75, 3.33) + rect(13.1, 3.4, 3.75, 3.33) + rect(9.35, 6.73, 3.75, 3.33) + rect(16.85, 6.73, 3.75, 3.33)
      + rect(5.6, 10.06, 3.75, 3.33) + rect(13.1, 10.06, 3.75, 3.33), evenodd=True)))
ui.append(icon('pin', G('M12 2a7.2 7.2 0 0 1 7.2 7.2c0 5.2-7.2 13-7.2 13S4.8 14.4 4.8 9.2A7.2 7.2 0 0 1 12 2z' + circle(12, 9.2, 2.7), evenodd=True)))
ui.append(icon('route', G(circle(5, 18.6, 2.6) + circle(19, 5.4, 2.6)),
    G(line((7.6, 17.6), (10, 16.6)) + line((12.2, 15.2), (13.4, 13)) + line((14, 10.6), (14.6, 8.4)) + line((15.8, 6.8), (16.8, 6.2)), stroke=1.8)))
ui.append(icon('locate', G(circle(12, 12, 6.6), stroke=2.2), G(circle(12, 12, 2.6)),
    G(line((12, 1.8), (12, 5)) + line((12, 19), (12, 22.2)) + line((1.8, 12), (5, 12)) + line((19, 12), (22.2, 12)), stroke=2.2)))
ui.append(icon('speed', G('M3.6 17.2a8.6 8.6 0 1 1 16.8 0', stroke=2.6), G(line((12, 15.2), (16.6, 9)), stroke=2.4), G(circle(12, 15.2, 2.2))))

ui2 = []
ui2.append(icon('mail', G(rect(2.2, 4.8, 19.6, 14.4, 2) + poly((4.2, 6.6), (12, 12.6), (19.8, 6.6), (19.8, 8.8), (12, 14.8), (4.2, 8.8)), evenodd=True)))
ui2.append(icon('close', G(line((5.4, 5.4), (18.6, 18.6)) + line((18.6, 5.4), (5.4, 18.6)), stroke=3.2)))
ui2.append(icon('check', G(line((3.8, 12.6), (9.4, 18.2), (20.2, 6.4)), stroke=3.2)))
ui2.append(icon('plus', G(line((12, 3.8), (12, 20.2)) + line((3.8, 12), (20.2, 12)), stroke=3.2)))
ui2.append(icon('minus', G(line((3.8, 12), (20.2, 12)), stroke=3.2)))
ui2.append(icon('star', G(star(12, 12.6, 10.2, 4.5))))
ui2.append(icon('trophy', G('M6 2.8h12v5.4a6 6 0 0 1-12 0z'), G('M6 4.8H3.4v1.6a3.6 3.6 0 0 0 3.6 3.6M18 4.8h2.6v1.6a3.6 3.6 0 0 1-3.6 3.6', stroke=1.7),
    G(rect(10.8, 13.6, 2.4, 4.2) + rect(6.8, 17.6, 10.4, 3.4, 1))))
ui2.append(icon('chart_up', G('M3 3v18h18', stroke=2), G(line((6, 16), (10, 11.6), (13.4, 14), (18.4, 8.4)), stroke=2.4), G(poly((20.6, 5.6), (15, 6.4), (19.8, 11.2)))))
ui2.append(icon('chart_down', G('M3 3v18h18', stroke=2), G(line((6, 7), (10, 11.4), (13.4, 9.6), (18, 15)), stroke=2.4), G(poly((20.2, 17.8), (19.6, 12.2), (14.6, 16.8)))))
ui2.append(icon('lock', G('M8 10.4V7.2a4 4 0 0 1 8 0v3.2', stroke=2.4), G(rect(4.6, 10, 14.8, 11.6, 2.2) + circle(12, 14.8, 1.7) + rect(11.25, 15, 1.5, 3.6, .6), evenodd=True)))
ui2.append(icon('unlock', G('M8 10.4V7.2a4 4 0 0 1 7.8-1.4', stroke=2.4), G(rect(4.6, 10, 14.8, 11.6, 2.2) + circle(12, 14.8, 1.7) + rect(11.25, 15, 1.5, 3.6, .6), evenodd=True)))
ui2.append(icon('warning', G('M10.3 3.4a2 2 0 0 1 3.4 0l8.4 14.6a2 2 0 0 1-1.7 3H3.6a2 2 0 0 1-1.7-3z' + rect(10.9, 8, 2.2, 7, 1.1) + circle(12, 17.6, 1.3), evenodd=True)))
ui2.append(icon('info', G(circle(12, 12, 10) + circle(12, 7.2, 1.5) + rect(10.85, 9.8, 2.3, 8, 1.1), evenodd=True)))
ui2.append(icon('search', G(circle(10.2, 10.2, 6.4), stroke=2.6), G(line((15, 15), (20.8, 20.8)), stroke=3.2)))
ui2.append(icon('trash', G(rect(3.6, 4.8, 16.8, 2.4, 1.1) + rect(9, 2.4, 6, 2.6, 1)),
    G(poly((5.2, 8.2), (18.8, 8.2), (17.4, 21.4), (6.6, 21.4)) + rect(8.4, 10.6, 1.6, 8.4, .8) + rect(11.2, 10.6, 1.6, 8.4, .8) + rect(14, 10.6, 1.6, 8.4, .8), evenodd=True)))
ui2.append(icon('cart_add', G(line((1.4, 3), (4, 3), (6.8, 16.6), (18.4, 16.6)), stroke=1.8), wheels(8.4, 17.6, y=20.2, r=1.7),
    G(poly((4.4, 5.6), (21, 5.6), (19, 14), (6.2, 14)) + poly((11.9, 6.8), (13.7, 6.8), (13.7, 9.1), (16, 9.1), (16, 10.9), (13.7, 10.9), (13.7, 13.2), (11.9, 13.2), (11.9, 10.9), (9.6, 10.9), (9.6, 9.1), (11.9, 9.1)), evenodd=True)))
ui2.append(icon('receipt', G('M5 2.4h14v19.2l-2.3-1.6-2.4 1.6-2.3-1.6-2.3 1.6-2.4-1.6L5 21.6z' + rect(8, 6.4, 8, 1.6, .8) + rect(8, 10, 8, 1.6, .8) + rect(8, 13.6, 5, 1.6, .8), evenodd=True)))
ui2.append(icon('cash_register', G(rect(5, 3, 9, 5.2, 1)), G(poly((4.4, 9), (19.6, 9), (21, 13), (3, 13)) + rect(7, 10.2, 1.6, 1.4) + rect(10, 10.2, 1.6, 1.4) + rect(13, 10.2, 1.6, 1.4), evenodd=True),
    G(rect(2.6, 13.6, 18.8, 7.6, 1.2) + rect(5, 16.6, 14, 1.4, .6), evenodd=True)))
ui2.append(icon('price_tag', G(poly((2.6, 11.4), (11, 3), (20.8, 3.2), (21, 13), (12.6, 21.4)) + circle(16.6, 7.4, 1.8), evenodd=True)))
ui2.append(icon('delivery', G(rect(6, 5.2, 10.6, 11, 1) + 'M17.2 8.6h3l2.2 3.6v4h-5.2z' + poly((18, 9.6), (19.6, 9.6), (21, 12), (18, 12)), evenodd=True), wheels(9.6, 19, y=17.6, r=1.9),
    G(line((1.4, 7.6), (4.4, 7.6)) + line((.6, 10.8), (4.4, 10.8)) + line((1.4, 14), (4.4, 14)), stroke=1.6)))
ui2.append(icon('box', G(poly((12, 2.4), (21.2, 7), (12, 11.6), (2.8, 7)), alpha=.7), G(poly((2.8, 7), (12, 11.6), (12, 21.6), (2.8, 17))),
    G(poly((21.2, 7), (12, 11.6), (12, 21.6), (21.2, 17)), alpha=.85), G(poly((7.6, 4.8), (10.2, 3.5), (19.4, 8.1), (16.8, 9.4)), alpha=.45)))
ui2.append(icon('furniture', G(rect(5, 3.6, 14, 9.6, 2.6)), G(rect(5, 13, 14, 4.4, 1.2)), G(rect(2.2, 8.8, 3.6, 9.6, 1.6) + rect(18.2, 8.8, 3.6, 9.6, 1.6)),
    G(rect(5, 18, 2.2, 3.4, .6) + rect(16.8, 18, 2.2, 3.4, .6))))
ui2.append(icon('broom', G(line((19.6, 2.6), (11.6, 12.2)), stroke=2.2), G(poly((9.2, 10.4), (14, 14.2), (10.4, 21.6), (2.4, 19.8), (4.2, 15.6))),
    G(line((5.2, 19.4), (8.6, 15.4)) + line((7.8, 20.6), (10.6, 16.6)), stroke=.9, alpha=.5)))
ui2.append(icon('camera', G(poly((2.6, 7.8), (15.6, 4), (17.8, 10.4), (4.8, 14.2)) + circle(5.8, 10.4, 1.4), evenodd=True),
    G(line((14.2, 11.6), (14.2, 17.4), (19.6, 17.4)), stroke=2.2), G(rect(19.2, 13.8, 2.6, 7, .8))))
ui2.append(icon('door_enter', G(rect(10, 2.6, 11, 18.8, 1.2) + circle(13, 12.2, 1.1), evenodd=True), G(line((1.8, 12), (7.6, 12)), stroke=2.4), G(poly((10.4, 12), (6.6, 8.2), (6.6, 15.8)))))
ui2.append(icon('door_exit', G(rect(3, 2.6, 11, 18.8, 1.2) + circle(11, 12.2, 1.1), evenodd=True), G(line((15.4, 12), (19.4, 12)), stroke=2.4), G(poly((22.6, 12), (18.8, 8.2), (18.8, 15.8)))))
ui2.append(icon('handshake', G(poly((1.4, 9.6), (5.6, 6.4), (9.4, 8), (13.6, 6.8), (17.4, 7.4), (11, 13.2), (8.8, 11.2))),
    G(poly((22.6, 9.6), (18.4, 6.4), (14.8, 8.6), (9.4, 13.8), (11.6, 16.8), (13.8, 18.6), (16.4, 16.8), (19.8, 13.6)), alpha=.75),
    G(rect(.8, 8.8, 3, 6.8, .8) + rect(20.2, 8.8, 3, 6.8, .8))))
ui2.append(icon('swords', G(poly((3, 4.6), (4.6, 3), (17.2, 15.6), (15.6, 17.2)) + poly((13.8, 18.8), (18.8, 13.8), (19.8, 14.8), (14.8, 19.8)) + poly((17.6, 18.8), (18.8, 17.6), (21.6, 20.4), (20.4, 21.6))),
    G(poly((21, 4.6), (19.4, 3), (6.8, 15.6), (8.4, 17.2)) + poly((10.2, 18.8), (5.2, 13.8), (4.2, 14.8), (9.2, 19.8)) + poly((6.4, 18.8), (5.2, 17.6), (2.4, 20.4), (3.6, 21.6)), alpha=.75)))
ui2.append(icon('megaphone', G(poly((4.2, 9), (15.2, 3.8), (15.2, 20.2), (4.2, 15))), G(rect(1.8, 8.8, 3.4, 6.4, 1)), G(poly((6.8, 15), (9.8, 15.6), (10.6, 20.8), (7.6, 20.8))),
    G('M18 8.6a5 5 0 0 1 0 6.8M20.4 6.2a8.4 8.4 0 0 1 0 11.6', stroke=1.8)))
ui2.append(icon('lightbulb', G('M12 2.4a7 7 0 0 1 4.2 12.6c-.8.6-1.2 1.4-1.2 2.4v.6H9v-.6c0-1-.4-1.8-1.2-2.4A7 7 0 0 1 12 2.4z'), G(rect(9, 18.8, 6, 1.6, .7) + rect(10, 21, 4, 1.4, .7))))
ui2.append(icon('flag', G(rect(3.8, 2.4, 2, 19.4, .8)), G('M6 3.8c3-1.6 5 1.6 8 0s4.2-1 6.2 0v9.2c-2-1-3.2-1.6-6.2 0s-5-1.6-8 0z')))
ui2.append(icon('dice', G(rect(2.8, 2.8, 18.4, 18.4, 4.2) + circle(7.8, 7.8, 1.7) + circle(16.2, 16.2, 1.7) + circle(12, 12, 1.7) + circle(16.2, 7.8, 1.7) + circle(7.8, 16.2, 1.7), evenodd=True)))
ui2.append(icon('music', G(poly((8.6, 5), (20.2, 2.4), (20.2, 5.6), (8.6, 8.2))), G(rect(8, 5, 1.8, 12.8) + rect(18.4, 2.6, 1.8, 12.6)),
    G(ellipse(6.2, 17.8, 3.2, 2.3) + ellipse(16.6, 15.4, 3.2, 2.3))))
ui2.append(icon('radio', G(line((5.4, 8), (17.6, 2.8)), stroke=1.6), G(rect(2.4, 7.6, 19.2, 13.6, 2.2) + circle(8.2, 14.4, 3.6) + rect(14.4, 11, 5, 1.6, .8) + rect(14.4, 14.4, 5, 1.6, .8) + rect(14.4, 17.8, 3, 1.6, .8), evenodd=True)))
ui2.append(icon('sleep', G('M14.6 3.2a8.6 8.6 0 1 0 6.6 13.8A7 7 0 0 1 14.6 3.2z'), G(line((15.6, 3.6), (19.6, 3.6), (15.6, 8.4), (19.6, 8.4)), stroke=1.4)))
ui2.append(icon('eat', G(circle(12, 12.4, 6.6) + circle(12, 12.4, 4.6), evenodd=True), G(circle(12, 12.4, 3.2), alpha=.6),
    G(line((2.4, 4), (2.4, 9)) + line((4, 4), (4, 9)) + 'M2.4 9a.8.8 0 0 0 1.6 0' + line((3.2, 9), (3.2, 20.4)), stroke=1.2),
    G('M20.6 4c1.4.8 2 2.8 2 5.6h-2zM20.6 9h1.8v11.4h-1.8z')))
ui2.append(icon('people', G(circle(16, 7.6, 3) + 'M10.4 19.4a5.6 5.4 0 0 1 11.2 0z', alpha=.65), G(circle(8.8, 8, 3.4) + 'M2.4 20.6a6.4 6 0 0 1 12.8 0z')))
ui2.append(icon('employee', G(circle(12, 6.8, 3.8)), G('M4.4 21.4a7.6 7.2 0 0 1 15.2 0z' + poly((11, 14.2), (13, 14.2), (13.6, 18.4), (12, 20), (10.4, 18.4)), evenodd=True)))
ui2.append(icon('area', G(rect(4, 4, 16, 16, 1.2), stroke=2.2), G(line((8, 16), (16, 8)), stroke=1.8), G(poly((17.2, 6.8), (12.4, 7.6), (16.4, 11.6)) + poly((6.8, 17.2), (7.6, 12.4), (11.6, 16.4)))))
ui2.append(icon('customers', G(circle(8, 6.4, 3.2) + 'M2 20.6a6 6 0 0 1 12 0z'), G(poly((13.2, 9.6), (21.6, 9.6), (22.4, 21.4), (12.4, 21.4)) + rect(15.4, 11, 4, 1.8, .9), evenodd=True)))
ui2.append(icon('student', G(poly((12, 2.6), (22.4, 7.4), (12, 12.2), (1.6, 7.4))), G(line((20.4, 8.4), (20.4, 12.6)), stroke=1.2),
    G('M4.4 13.2c2.8-1 5.2-.8 7.6.9 2.4-1.7 4.8-1.9 7.6-.9v7.6c-2.8-1-5.2-.8-7.6.9-2.4-1.7-4.8-1.9-7.6-.9z')))
ui2.append(icon('loan', G(rect(2, 5.6, 20, 12.8, 1.8) + circle(9, 9.8, 1.7) + circle(15, 14.2, 1.7) + poly((14.6, 7.6), (16, 8.8), (9.4, 16.4), (8, 15.2)), evenodd=True)))
ui2.append(icon('bankrupt', G(ellipse(11.4, 13, 8.4, 6.2) + poly((11.4, 6.8), (13, 9.6), (10.4, 12.2), (12.8, 15), (11, 19.2), (9.8, 19.2), (11.4, 15.2), (8.8, 12.4), (11, 9.8), (9.8, 6.8)), evenodd=True),
    G(poly((5.6, 8.6), (6.4, 4.6), (9.2, 7.6))), G(rect(5.6, 17.6, 2.4, 4, .6) + rect(14.4, 17.6, 2.4, 4, .6)), G(rect(19.2, 11, 3, 3.6, 1.2))))
ui2.append(icon('crown', G(poly((2.6, 6.6), (7.8, 11), (12, 3.6), (16.2, 11), (21.4, 6.6), (19.2, 17.6), (4.8, 17.6))), G(rect(4.8, 18.6, 14.4, 2.6, .8))))
sun_rays = ''.join(line((12 + 7 * math.cos(math.radians(a)), 12 + 7 * math.sin(math.radians(a))), (12 + 10 * math.cos(math.radians(a)), 12 + 10 * math.sin(math.radians(a)))) for a in range(0, 360, 45))
ui2.append(icon('sun', G(circle(12, 12, 4.8)), G(sun_rays, stroke=2.2)))
small_rays = ''.join(line((9 + 5.6 * math.cos(math.radians(a)), 9 + 5.6 * math.sin(math.radians(a))), (9 + 7.8 * math.cos(math.radians(a)), 9 + 7.8 * math.sin(math.radians(a)))) for a in (180, 225, 270, 315))
ui2.append(icon('partly_cloudy', G(circle(9, 9, 3.8), alpha=.7), G(small_rays, stroke=1.8, alpha=.7), G(cloud(3.4, 4.6, .82))))
ui2.append(icon('cloud', G(cloud(0, 0, 1))))
ui2.append(icon('rain', G(cloud(1.2, -2.8, .9)), G(line((8, 17.6), (6.6, 21)) + line((12.4, 17.6), (11, 21)) + line((16.8, 17.6), (15.4, 21)), stroke=1.8)))
ui2.append(icon('storm', G(cloud(1.2, -3.2, .9)), G(poly((12.4, 13.6), (8.4, 18.6), (11.2, 18.6), (9.6, 22.6), (14.8, 16.8), (12, 16.8), (13.6, 13.6)))))
flake = ''.join(line((12 + 9.6 * math.cos(math.radians(a)), 12 + 9.6 * math.sin(math.radians(a))), (12 - 9.6 * math.cos(math.radians(a)), 12 - 9.6 * math.sin(math.radians(a)))) for a in (90, 30, 150))
branches = ''
for a in range(30, 390, 60):
    ca, sa = math.cos(math.radians(a)), math.sin(math.radians(a))
    px, py = 12 + 6.4 * ca, 12 + 6.4 * sa
    for d in (-1, 1):
        b = math.radians(a + d * 42)
        branches += line((px, py), (px + 2.8 * math.cos(b), py + 2.8 * math.sin(b)))
ui2.append(icon('snow', G(flake, stroke=1.9), G(branches, stroke=1.5)))
ui2.append(icon('fog', G(cloud(1.4, -3.4, .85), alpha=.75), G(line((3, 16.6), (21, 16.6)) + line((5, 20), (19, 20)), stroke=1.9)))
ui2.append(icon('thermometer', G(rect(9.4, 2.4, 5.2, 14.4, 2.6), stroke=1.6), G(circle(12, 17.8, 3.8)), G(rect(11.1, 8, 1.8, 9, .9))))
ui2.append(icon('umbrella', G('M2.6 12.4a9.4 9 0 0 1 18.8 0q-1.57-1.3-3.13 0q-1.57-1.3-3.13 0q-1.57-1.3-3.14 0q-1.57-1.3-3.13 0q-1.57-1.3-3.13 0q-1.57-1.3-3.14 0z'),
    G(line((12, 12), (12, 19.4)), stroke=1.6), G('M12 19.4a1.8 1.8 0 0 1-3.6 0', stroke=1.6)))
ui2.append(icon('wind', G('M2.6 8.4h11.2a3 3 0 1 0-3-3M2.6 12.2h15.6a3 3 0 1 1-3 3M2.6 16h7.6', stroke=2)))
ui2.append(icon('arrow_up', G('M10.3 4.6a2 2 0 0 1 3.4 0l7.4 12.2a1.6 1.6 0 0 1-1.4 2.4H4.3a1.6 1.6 0 0 1-1.4-2.4z')))
ui2.append(icon('arrow_down', G('M10.3 19.4a2 2 0 0 0 3.4 0l7.4-12.2a1.6 1.6 0 0 0-1.4-2.4H4.3a1.6 1.6 0 0 0-1.4 2.4z')))
ui2.append(icon('arrow_left', G('M4.6 10.3a2 2 0 0 0 0 3.4l12.2 7.4a1.6 1.6 0 0 0 2.4-1.4V4.3a1.6 1.6 0 0 0-2.4-1.4z')))
ui2.append(icon('arrow_right', G('M19.4 10.3a2 2 0 0 1 0 3.4L7.2 21.1a1.6 1.6 0 0 1-2.4-1.4V4.3a1.6 1.6 0 0 1 2.4-1.4z')))

open(os.path.join(OUT, 'UiIcons.kt'), 'w').write(kotlin_file('UiIcons', 'Glyphs for the HUD, phone apps, vehicles and navigation.', ui))
open(os.path.join(OUT, 'UiIcons2.kt'), 'w').write(kotlin_file('UiIcons2', 'Glyphs for actions, status, shopping, weather and messages.', ui2))
print(len(ui), len(ui2))
