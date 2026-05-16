from pathlib import Path
from math import atan2, cos, sin, pi

from reportlab.lib import colors
from reportlab.lib.colors import Color, HexColor
from reportlab.lib.pagesizes import A3, landscape
from reportlab.lib.utils import simpleSplit
from reportlab.pdfgen import canvas


PAGE_WIDTH, PAGE_HEIGHT = landscape(A3)
MARGIN = 40

BG = HexColor("#F8FAFC")
TEXT = HexColor("#0F172A")
MUTED = HexColor("#475569")
LIGHT = HexColor("#CBD5E1")
SOFT = HexColor("#E2E8F0")
TEAL = HexColor("#0F766E")
TEAL_FILL = HexColor("#CCFBF1")
BLUE = HexColor("#1D4ED8")
BLUE_FILL = HexColor("#DBEAFE")
AMBER = HexColor("#B45309")
AMBER_FILL = HexColor("#FEF3C7")
GREEN = HexColor("#166534")
GREEN_FILL = HexColor("#DCFCE7")
ROSE = HexColor("#BE123C")
ROSE_FILL = HexColor("#FFE4E6")
SLATE_FILL = HexColor("#E2E8F0")


def split_lines(text, width, font_name="Helvetica", font_size=10):
    return simpleSplit(text, font_name, font_size, width)


def draw_page_background(pdf):
    pdf.setFillColor(BG)
    pdf.rect(0, 0, PAGE_WIDTH, PAGE_HEIGHT, stroke=0, fill=1)


def draw_header(pdf, title, subtitle, page_label):
    pdf.setFillColor(TEAL)
    pdf.rect(0, PAGE_HEIGHT - 78, PAGE_WIDTH, 78, stroke=0, fill=1)

    pdf.setFillColor(colors.white)
    pdf.setFont("Helvetica-Bold", 28)
    pdf.drawString(MARGIN, PAGE_HEIGHT - 42, title)

    pdf.setFont("Helvetica", 12)
    pdf.drawString(MARGIN, PAGE_HEIGHT - 60, subtitle)

    pdf.setFont("Helvetica-Bold", 12)
    label_width = pdf.stringWidth(page_label, "Helvetica-Bold", 12)
    pdf.drawString(PAGE_WIDTH - MARGIN - label_width, PAGE_HEIGHT - 42, page_label)


def draw_footer(pdf, text):
    pdf.setStrokeColor(SOFT)
    pdf.setLineWidth(1)
    pdf.line(MARGIN, 24, PAGE_WIDTH - MARGIN, 24)
    pdf.setFillColor(MUTED)
    pdf.setFont("Helvetica", 9)
    pdf.drawString(MARGIN, 10, text)


def draw_paragraph(pdf, text, x, top_y, width, font_name="Helvetica", font_size=11,
                   color=TEXT, leading=None):
    leading = leading or (font_size + 3)
    lines = split_lines(text, width, font_name, font_size)
    cursor = top_y
    pdf.setFillColor(color)
    pdf.setFont(font_name, font_size)
    for line in lines:
        pdf.drawString(x, cursor, line)
        cursor -= leading
    return cursor


def draw_note_box(pdf, x, y, w, h, title, lines, fill_color=colors.white,
                  stroke_color=LIGHT, title_color=TEXT):
    pdf.setFillColor(fill_color)
    pdf.setStrokeColor(stroke_color)
    pdf.setLineWidth(1.2)
    pdf.roundRect(x, y, w, h, 14, stroke=1, fill=1)

    pdf.setFillColor(title_color)
    pdf.setFont("Helvetica-Bold", 14)
    pdf.drawString(x + 16, y + h - 24, title)

    text_y = y + h - 46
    for line in lines:
        text_y = draw_paragraph(
            pdf,
            line,
            x + 16,
            text_y,
            w - 32,
            font_name="Helvetica",
            font_size=10,
            color=MUTED,
            leading=13,
        ) - 4


def draw_entity_box(pdf, x, y, w, h, title, fields, title_fill, body_fill=colors.white):
    pdf.setFillColor(body_fill)
    pdf.setStrokeColor(LIGHT)
    pdf.setLineWidth(1.2)
    pdf.roundRect(x, y, w, h, 12, stroke=1, fill=1)

    header_h = 26
    pdf.setFillColor(title_fill)
    pdf.roundRect(x, y + h - header_h, w, header_h, 12, stroke=0, fill=1)
    pdf.setFillColor(TEXT)
    pdf.setFont("Helvetica-Bold", 12)
    pdf.drawString(x + 12, y + h - 18, title)

    pdf.setStrokeColor(SOFT)
    pdf.line(x + 10, y + h - header_h - 4, x + w - 10, y + h - header_h - 4)

    cursor = y + h - header_h - 16
    pdf.setFont("Helvetica", 9)
    pdf.setFillColor(TEXT)
    for field in fields:
        wrapped = split_lines(field, w - 24, "Helvetica", 9)
        for line in wrapped:
            pdf.drawString(x + 12, cursor, line)
            cursor -= 11
        cursor -= 2


def draw_component_box(pdf, x, y, w, h, title, subtitle, lines, fill_color, title_color=TEXT):
    pdf.setFillColor(fill_color)
    pdf.setStrokeColor(LIGHT)
    pdf.setLineWidth(1.5)
    pdf.roundRect(x, y, w, h, 14, stroke=1, fill=1)

    pdf.setFillColor(title_color)
    pdf.setFont("Helvetica-Bold", 13)
    pdf.drawString(x + 12, y + h - 20, title)

    pdf.setFillColor(MUTED)
    pdf.setFont("Helvetica-Bold", 9)
    pdf.drawString(x + 12, y + h - 35, subtitle)

    cursor = y + h - 52
    pdf.setFillColor(TEXT)
    pdf.setFont("Helvetica", 9)
    for line in lines:
        wrapped = split_lines(line, w - 24, "Helvetica", 9)
        for wrapped_line in wrapped:
            pdf.drawString(x + 12, cursor, wrapped_line)
            cursor -= 10
        cursor -= 3


def draw_label(pdf, text, x, y, fill=colors.white, stroke=LIGHT, text_color=MUTED):
    width = pdf.stringWidth(text, "Helvetica-Bold", 9) + 16
    pdf.setFillColor(fill)
    pdf.setStrokeColor(stroke)
    pdf.roundRect(x, y - 9, width, 18, 8, stroke=1, fill=1)
    pdf.setFillColor(text_color)
    pdf.setFont("Helvetica-Bold", 9)
    pdf.drawCentredString(x + width / 2, y - 2, text)


def draw_arrow(pdf, start, end, color=TEXT, width=1.8, dashed=False, label=None,
               label_pos=0.5, label_dx=0, label_dy=0):
    x1, y1 = start
    x2, y2 = end
    pdf.saveState()
    pdf.setStrokeColor(color)
    pdf.setFillColor(color)
    pdf.setLineWidth(width)
    if dashed:
        pdf.setDash(6, 4)
    pdf.line(x1, y1, x2, y2)
    pdf.restoreState()

    angle = atan2(y2 - y1, x2 - x1)
    arrow_size = 9
    left_x = x2 - arrow_size * cos(angle - pi / 6)
    left_y = y2 - arrow_size * sin(angle - pi / 6)
    right_x = x2 - arrow_size * cos(angle + pi / 6)
    right_y = y2 - arrow_size * sin(angle + pi / 6)
    pdf.setFillColor(color)
    pdf.setStrokeColor(color)
    pdf.line(x2, y2, left_x, left_y)
    pdf.line(x2, y2, right_x, right_y)

    if label:
        lx = x1 + (x2 - x1) * label_pos + label_dx
        ly = y1 + (y2 - y1) * label_pos + label_dy
        draw_label(pdf, label, lx - 8, ly)


def draw_poly_arrow(pdf, points, color=TEXT, width=1.8, dashed=False, label=None,
                    label_anchor=None, label_dx=0, label_dy=0):
    pdf.saveState()
    pdf.setStrokeColor(color)
    pdf.setFillColor(color)
    pdf.setLineWidth(width)
    if dashed:
        pdf.setDash(6, 4)
    for index in range(len(points) - 1):
        x1, y1 = points[index]
        x2, y2 = points[index + 1]
        pdf.line(x1, y1, x2, y2)
    pdf.restoreState()

    x1, y1 = points[-2]
    x2, y2 = points[-1]
    angle = atan2(y2 - y1, x2 - x1)
    arrow_size = 9
    left_x = x2 - arrow_size * cos(angle - pi / 6)
    left_y = y2 - arrow_size * sin(angle - pi / 6)
    right_x = x2 - arrow_size * cos(angle + pi / 6)
    right_y = y2 - arrow_size * sin(angle + pi / 6)
    pdf.line(x2, y2, left_x, left_y)
    pdf.line(x2, y2, right_x, right_y)

    if label and label_anchor:
        draw_label(pdf, label, label_anchor[0] + label_dx, label_anchor[1] + label_dy)


def draw_centered_step_box(pdf, x, y, w, h, title, detail, fill_color):
    pdf.setFillColor(fill_color)
    pdf.setStrokeColor(LIGHT)
    pdf.setLineWidth(1.2)
    pdf.roundRect(x, y, w, h, 12, stroke=1, fill=1)

    pdf.setFillColor(TEXT)
    pdf.setFont("Helvetica-Bold", 12)
    pdf.drawCentredString(x + w / 2, y + h - 20, title)

    lines = split_lines(detail, w - 26, "Helvetica", 9)
    pdf.setFont("Helvetica", 9)
    cursor = y + h - 36
    for line in lines:
        pdf.drawCentredString(x + w / 2, cursor, line)
        cursor -= 11


def draw_relation(pdf, start, end, card_text, color=BLUE, dashed=False):
    draw_arrow(pdf, start, end, color=color, dashed=dashed, width=1.5)
    mid_x = (start[0] + end[0]) / 2
    mid_y = (start[1] + end[1]) / 2
    draw_label(pdf, card_text, mid_x - 20, mid_y + 10, fill=colors.white, stroke=SOFT, text_color=TEXT)


def page_cover(pdf):
    draw_page_background(pdf)
    draw_header(
        pdf,
        "ConnectHub UML and ER Diagrams",
        "Readable project architecture and data model views generated from the current backend codebase.",
        "Overview",
    )

    pdf.setFillColor(TEXT)
    pdf.setFont("Helvetica-Bold", 24)
    pdf.drawString(MARGIN, PAGE_HEIGHT - 135, "What this PDF contains")

    intro = (
        "This document focuses on the current microservice layout, the most important runtime flows, "
        "and the logical entity relationships used by the backend. Cross-service references are shown "
        "as logical IDs because the services own separate databases."
    )
    draw_paragraph(pdf, intro, MARGIN, PAGE_HEIGHT - 165, 640, font_size=12, color=MUTED, leading=16)

    draw_note_box(
        pdf,
        MARGIN,
        PAGE_HEIGHT - 390,
        340,
        170,
        "Included pages",
        [
            "1. Deployment UML view: gateway, discovery, services, databases, Kafka, Redis, and providers.",
            "2. Interaction UML view: real-time chat, translation-credit flow, and payment top-up flow.",
            "3. ER view: key JPA entities and their logical relationships across services.",
        ],
        fill_color=colors.white,
        stroke_color=LIGHT,
        title_color=TEXT,
    )

    draw_note_box(
        pdf,
        410,
        PAGE_HEIGHT - 390,
        340,
        170,
        "How to read the diagrams",
        [
            "Blue arrows show synchronous HTTP or Feign calls.",
            "Amber arrows show Kafka or event-driven communication.",
            "Gray dashed arrows show service discovery or logical references.",
            "Green database boxes mean persistent state. Teal boxes show service components.",
        ],
        fill_color=colors.white,
        stroke_color=LIGHT,
        title_color=TEXT,
    )

    draw_note_box(
        pdf,
        780,
        PAGE_HEIGHT - 390,
        370,
        170,
        "Source evidence used",
        [
            "README.md and docs/PRODUCTION_DOCUMENTATION.md for ports, runtime topology, and flows.",
            "application.yaml files for datasource, Redis, Kafka, and Eureka wiring.",
            "Entity classes in auth-service, room-service, message-service, notification-service, and payment-service.",
        ],
        fill_color=colors.white,
        stroke_color=LIGHT,
        title_color=TEXT,
    )

    pdf.setFillColor(TEXT)
    pdf.setFont("Helvetica-Bold", 18)
    pdf.drawString(MARGIN, PAGE_HEIGHT - 450, "Service inventory")

    cards = [
        ("gateway-service", "8080", "Entry point for HTTP and WebSocket"),
        ("eureka-server", "9000", "Service registry and discovery"),
        ("auth-service", "9002", "Users, contacts, JWT, credits"),
        ("room-service", "9003", "Rooms and room members"),
        ("message-service", "9004", "Message persistence and search"),
        ("notification-service", "9007", "Unread notifications"),
        ("presence-service", "9012", "Redis-backed presence"),
        ("translation-service", "9013", "Gemini and LibreTranslate wrapper"),
        ("websocket-service", "9014", "STOMP and real-time fan-out"),
        ("payment-service", "9015", "Razorpay orders and credit sync"),
    ]

    x_positions = [MARGIN, 310, 580, 850]
    y_positions = [PAGE_HEIGHT - 560, PAGE_HEIGHT - 645, PAGE_HEIGHT - 730]
    card_width = 250
    card_height = 64

    card_index = 0
    for row_y in y_positions:
        for x in x_positions:
            if card_index >= len(cards):
                break
            title, port, detail = cards[card_index]
            pdf.setFillColor(colors.white)
            pdf.setStrokeColor(LIGHT)
            pdf.roundRect(x, row_y, card_width, card_height, 12, stroke=1, fill=1)
            pdf.setFillColor(TEXT)
            pdf.setFont("Helvetica-Bold", 11)
            pdf.drawString(x + 12, row_y + 41, title)
            pdf.setFont("Helvetica-Bold", 9)
            pdf.setFillColor(TEAL)
            pdf.drawString(x + 12, row_y + 26, f"Port {port}")
            pdf.setFillColor(MUTED)
            pdf.setFont("Helvetica", 8.5)
            for offset, line in enumerate(split_lines(detail, card_width - 24, "Helvetica", 8.5)):
                pdf.drawString(x + 12, row_y + 12 - (offset * 10), line)
            card_index += 1

    draw_footer(pdf, "Generated for the ConnectHub backend workspace on 2026-05-09.")
    pdf.showPage()


def page_deployment_uml(pdf):
    draw_page_background(pdf)
    draw_header(
        pdf,
        "UML Deployment View",
        "High-level component diagram of the ConnectHub backend runtime topology.",
        "Page 1",
    )

    pdf.setStrokeColor(SOFT)
    pdf.setFillColor(Color(1, 1, 1, alpha=0.45))
    pdf.roundRect(28, 68, PAGE_WIDTH - 56, PAGE_HEIGHT - 168, 18, stroke=1, fill=1)

    draw_component_box(pdf, 90, 660, 180, 78, "Web / Mobile Client", "Consumer", [
        "Browses HTTP APIs through gateway",
        "Uses STOMP over WebSocket for live chat",
    ], TEAL_FILL)
    draw_component_box(pdf, 320, 650, 190, 92, "gateway-service", "Port 8080", [
        "Routes /auth, /rooms, /messages, /payments, /ws",
        "JWT filtering for protected routes",
    ], BLUE_FILL)
    draw_component_box(pdf, 560, 650, 180, 92, "eureka-server", "Port 9000", [
        "Service registration",
        "Logical name resolution",
    ], SLATE_FILL)
    draw_component_box(pdf, 970, 635, 150, 78, "Kafka", "Event bus", [
        "chat.message.incoming",
        "chat.message.broadcast",
        "credit-topup-topic",
        "user.presence",
    ], AMBER_FILL)

    service_specs = [
        (70, 505, "auth-service", "9002", ["Users, contacts, JWT", "Credits and credit sync"], TEAL_FILL),
        (330, 505, "room-service", "9003", ["Rooms and members", "lastMessageAt updates"], TEAL_FILL),
        (590, 505, "message-service", "9004", ["Persist chat history", "Search, edits, reactions"], TEAL_FILL),
        (850, 505, "websocket-service", "9014", ["STOMP entry point", "Real-time room fan-out"], TEAL_FILL),
        (70, 315, "notification-service", "9007", ["Unread notification store", "Read and count APIs"], TEAL_FILL),
        (330, 315, "presence-service", "9012", ["Online/offline state", "Redis plus Kafka"], TEAL_FILL),
        (590, 315, "translation-service", "9013", ["Text translation wrapper", "Gemini with fallback"], TEAL_FILL),
        (850, 315, "payment-service", "9015", ["Razorpay orders", "Payment verify and receipts"], TEAL_FILL),
    ]

    for x, y, title, port, lines, fill in service_specs:
        draw_component_box(pdf, x, y, 210, 120, title, f"Port {port}", lines, fill)

    store_specs = [
        (90, 170, 170, 68, "MySQL", "connecthub_auth"),
        (350, 170, 170, 68, "MySQL / H2", "rooms"),
        (610, 170, 170, 68, "MySQL / H2 + Files", "messages and uploads"),
        (90, 80, 170, 60, "MySQL / H2", "notifications"),
        (350, 80, 170, 60, "Redis", "user:status:*"),
        (850, 170, 170, 68, "MySQL / H2", "payments"),
    ]

    for x, y, w, h, title, subtitle in store_specs:
        draw_component_box(pdf, x, y, w, h, title, "State store", [subtitle], GREEN_FILL)

    draw_component_box(pdf, 910, 200, 240, 82, "External providers", "Outbound integrations", [
        "Gemini and LibreTranslate",
        "Razorpay payment APIs",
        "SMTP for OTP and receipts",
    ], ROSE_FILL)

    draw_arrow(pdf, (270, 699), (320, 696), color=BLUE, label="HTTP", label_pos=0.55, label_dy=16)

    gateway_start = (415, 650)
    gateway_targets = [
        ((175, 625), "Route"),
        ((435, 625), "Route"),
        ((695, 625), "Route"),
        ((955, 625), "Route"),
        ((175, 435), "Route"),
        ((435, 435), "Route"),
        ((695, 435), "Route"),
        ((955, 435), "Route"),
    ]
    for target, label in gateway_targets:
        draw_arrow(pdf, gateway_start, target, color=HexColor("#64748B"), width=1.2)

    eureka_start = (650, 650)
    eureka_targets = [
        (175, 625), (435, 625), (695, 625), (955, 625),
        (175, 435), (435, 435), (695, 435), (955, 435), (415, 650)
    ]
    for target in eureka_targets:
        draw_arrow(pdf, eureka_start, target, color=HexColor("#94A3B8"), width=1.1, dashed=True)
    draw_label(pdf, "Register / discover", 700, 590, fill=colors.white, stroke=SOFT, text_color=MUTED)

    draw_arrow(pdf, (175, 505), (175, 238), color=GREEN, label="Persist", label_pos=0.52, label_dx=12)
    draw_arrow(pdf, (435, 505), (435, 238), color=GREEN, label="Persist", label_pos=0.52, label_dx=12)
    draw_arrow(pdf, (695, 505), (695, 238), color=GREEN, label="Persist", label_pos=0.52, label_dx=12)
    draw_arrow(pdf, (175, 315), (175, 140), color=GREEN, label="Persist", label_pos=0.5, label_dx=12)
    draw_arrow(pdf, (435, 315), (435, 140), color=GREEN, label="Read / write", label_pos=0.52, label_dx=12)
    draw_arrow(pdf, (955, 315), (935, 238), color=GREEN, label="Persist", label_pos=0.45, label_dx=12)

    kafka_points = {
        "message-service": (800, 565),
        "websocket-service": (1060, 565),
        "presence-service": (540, 380),
        "payment-service": (1060, 380),
        "auth-service": (280, 565),
    }
    draw_arrow(pdf, (800, 565), (970, 635), color=AMBER, label="Produce", label_pos=0.55, label_dy=14)
    draw_arrow(pdf, (970, 660), (1060, 565), color=AMBER, label="Consume", label_pos=0.5, label_dy=14)
    draw_arrow(pdf, (435, 435), (970, 635), color=AMBER, label="Produce", label_pos=0.6, label_dy=12)
    draw_arrow(pdf, (955, 435), (970, 635), color=AMBER, label="Produce", label_pos=0.55, label_dy=14)
    draw_arrow(pdf, (970, 650), (175, 600), color=AMBER, label="Top-up event", label_pos=0.64, label_dy=14)

    draw_arrow(pdf, (800, 375), (910, 240), color=ROSE, label="Calls", label_pos=0.55, label_dy=14)
    draw_arrow(pdf, (1060, 375), (1030, 282), color=ROSE, label="Calls", label_pos=0.45, label_dx=10)

    draw_note_box(
        pdf,
        890,
        65,
        250,
        100,
        "Reading note",
        [
            "This page emphasizes runtime placement: entry layer, services, stores, event bus, and external providers.",
            "Detailed request and event flows appear on the next page so this diagram stays readable.",
        ],
        fill_color=colors.white,
        stroke_color=LIGHT,
        title_color=TEXT,
    )

    draw_footer(pdf, "UML deployment view: based on README.md, docs/PRODUCTION_DOCUMENTATION.md, and service application.yaml files.")
    pdf.showPage()


def page_interaction_uml(pdf):
    draw_page_background(pdf)
    draw_header(
        pdf,
        "UML Interaction Views",
        "Three important business flows are separated so each one is easy to follow.",
        "Page 2",
    )

    sections = [
        (560, "1. Real-time chat flow"),
        (350, "2. Translation and credit flow"),
        (140, "3. Payment top-up flow"),
    ]

    for y, title in sections:
        pdf.setFillColor(colors.white)
        pdf.setStrokeColor(SOFT)
        pdf.roundRect(MARGIN, y - 30, PAGE_WIDTH - (2 * MARGIN), 170, 16, stroke=1, fill=1)
        pdf.setFillColor(TEXT)
        pdf.setFont("Helvetica-Bold", 16)
        pdf.drawString(MARGIN + 18, y + 116, title)

    chat_boxes = [
        (60, 590, 135, 70, "Client", "Send or receive chat"),
        (225, 590, 135, 70, "Gateway", "Public entry"),
        (390, 590, 150, 70, "WebSocket", "STOMP /app/chat.send"),
        (575, 590, 125, 70, "Kafka", "chat.message.incoming"),
        (735, 590, 155, 70, "Message", "Persist and enrich"),
        (930, 610, 150, 52, "Room", "Update lastMessageAt"),
        (930, 545, 150, 52, "Notification", "Persist offline alerts"),
    ]
    for x, y, w, h, title, detail in chat_boxes:
        draw_centered_step_box(pdf, x, y, w, h, title, detail, BLUE_FILL if title in {"Gateway", "Message"} else TEAL_FILL if title in {"WebSocket", "Room", "Notification"} else AMBER_FILL if title == "Kafka" else colors.white)

    draw_arrow(pdf, (195, 625), (225, 625), color=BLUE, label="1", label_pos=0.5, label_dy=14)
    draw_arrow(pdf, (360, 625), (390, 625), color=BLUE, label="2", label_pos=0.5, label_dy=14)
    draw_arrow(pdf, (540, 625), (575, 625), color=AMBER, label="3", label_pos=0.5, label_dy=14)
    draw_arrow(pdf, (700, 625), (735, 625), color=AMBER, label="4", label_pos=0.5, label_dy=14)
    draw_arrow(pdf, (890, 625), (930, 636), color=BLUE, label="5", label_pos=0.45, label_dy=12)
    draw_arrow(pdf, (890, 625), (930, 571), color=BLUE, label="6", label_pos=0.42, label_dy=-14)
    draw_poly_arrow(
        pdf,
        [(890, 615), (920, 615), (920, 690), (575, 690), (575, 660)],
        color=AMBER,
        label="7",
        label_anchor=(745, 706),
    )
    draw_poly_arrow(
        pdf,
        [(575, 660), (575, 715), (390, 715), (390, 660)],
        color=AMBER,
        label="8",
        label_anchor=(462, 732),
    )
    draw_poly_arrow(
        pdf,
        [(390, 660), (390, 715), (128, 715), (128, 660)],
        color=BLUE,
        label="9",
        label_anchor=(236, 732),
    )
    draw_paragraph(
        pdf,
        "Flow summary: the live message enters through WebSocket, is persisted asynchronously by message-service, updates room metadata, stores notifications, and is then broadcast back to subscribers.",
        60,
        570,
        1040,
        font_size=9.5,
        color=MUTED,
        leading=12,
    )

    translation_boxes = [
        (60, 380, 135, 70, "Client", "Requests translation"),
        (225, 380, 135, 70, "Gateway", "Routes /messages"),
        (390, 380, 155, 70, "Message", "Load message and orchestrate"),
        (580, 410, 155, 52, "Auth", "Consume 1 credit"),
        (580, 345, 155, 52, "Auth", "Refund if translation fails"),
        (775, 380, 165, 70, "Translation", "Provider wrapper"),
        (975, 410, 150, 52, "Gemini", "Primary provider"),
        (975, 345, 150, 52, "LibreTranslate", "Fallback provider"),
    ]
    for x, y, w, h, title, detail in translation_boxes:
        fill = BLUE_FILL if title == "Message" else TEAL_FILL if title in {"Auth", "Translation"} else ROSE_FILL if title in {"Gemini", "LibreTranslate"} else colors.white
        draw_centered_step_box(pdf, x, y, w, h, title, detail, fill)

    draw_arrow(pdf, (195, 415), (225, 415), color=BLUE, label="1", label_pos=0.5, label_dy=14)
    draw_arrow(pdf, (360, 415), (390, 415), color=BLUE, label="2", label_pos=0.5, label_dy=14)
    draw_arrow(pdf, (545, 425), (580, 436), color=BLUE, label="3", label_pos=0.5, label_dy=12)
    draw_arrow(pdf, (545, 415), (775, 415), color=BLUE, label="4", label_pos=0.5, label_dy=14)
    draw_arrow(pdf, (940, 425), (975, 436), color=ROSE, label="5", label_pos=0.5, label_dy=12)
    draw_arrow(pdf, (940, 405), (975, 371), color=ROSE, dashed=True, label="Fallback", label_pos=0.5, label_dy=-16)
    draw_poly_arrow(
        pdf,
        [(775, 395), (730, 395), (730, 371)],
        color=BLUE,
        dashed=True,
        label="Refund only on failure",
        label_anchor=(625, 320),
    )
    draw_poly_arrow(
        pdf,
        [(775, 405), (735, 405), (735, 415), (195, 415)],
        color=BLUE,
        label="6. Return translation",
        label_anchor=(400, 455),
    )

    draw_paragraph(
        pdf,
        "Flow summary: message-service deducts a translation credit before calling translation-service. If the provider path fails after consumption, it makes a best-effort refund call back to auth-service.",
        60,
        360,
        1040,
        font_size=9.5,
        color=MUTED,
        leading=12,
    )

    payment_boxes = [
        (60, 170, 135, 70, "Client", "Buys a plan"),
        (225, 170, 135, 70, "Gateway", "Routes /payments"),
        (390, 170, 155, 70, "Payment", "Create and verify order"),
        (590, 170, 155, 70, "Razorpay", "Provider APIs"),
        (790, 170, 125, 70, "Kafka", "credit-topup-topic"),
        (955, 170, 150, 70, "Auth", "Increase credit balance"),
    ]
    for x, y, w, h, title, detail in payment_boxes:
        fill = TEAL_FILL if title in {"Payment", "Auth"} else AMBER_FILL if title == "Kafka" else ROSE_FILL if title == "Razorpay" else colors.white
        draw_centered_step_box(pdf, x, y, w, h, title, detail, fill)

    draw_arrow(pdf, (195, 205), (225, 205), color=BLUE, label="1", label_pos=0.5, label_dy=14)
    draw_arrow(pdf, (360, 205), (390, 205), color=BLUE, label="2", label_pos=0.5, label_dy=14)
    draw_arrow(pdf, (545, 205), (590, 205), color=ROSE, label="3", label_pos=0.5, label_dy=14)
    draw_poly_arrow(
        pdf,
        [(745, 205), (780, 205), (780, 205), (790, 205)],
        color=AMBER,
        label="4. Publish top-up event",
        label_anchor=(630, 246),
    )
    draw_arrow(pdf, (915, 205), (955, 205), color=AMBER, label="5", label_pos=0.5, label_dy=14)
    draw_poly_arrow(
        pdf,
        [(465, 170), (465, 130), (1030, 130), (1030, 170)],
        color=ROSE,
        label="Receipt email",
        label_anchor=(660, 104),
    )

    draw_paragraph(
        pdf,
        "Flow summary: payment-service creates the provider order, verifies the payment, publishes a Kafka top-up event, and auth-service applies the purchased credits to the user's balance.",
        60,
        150,
        1040,
        font_size=9.5,
        color=MUTED,
        leading=12,
    )

    draw_footer(pdf, "UML interaction views: key chat, translation, and payment flows extracted from current service code and production documentation.")
    pdf.showPage()


def page_er(pdf):
    draw_page_background(pdf)
    draw_header(
        pdf,
        "ER Diagram",
        "Logical entity relationships across the ConnectHub services. Only key fields are shown to keep the page readable.",
        "Page 3",
    )

    draw_entity_box(
        pdf,
        60,
        540,
        260,
        190,
        "User",
        [
            "PK userId : UUID",
            "email, phoneNumber, username",
            "fullName, avatarUrl, bio",
            "preferredLanguage, role",
            "translationCreditsRemaining",
            "onlineStatus, lastSeenAt",
            "passwordResetToken, passwordResetTokenExpiry",
        ],
        TEAL_FILL,
    )
    draw_entity_box(
        pdf,
        60,
        350,
        260,
        150,
        "Contact",
        [
            "PK contactId : UUID",
            "ownerUserId -> User.userId",
            "contactUserId -> User.userId",
            "contactEmail, contactName, nickname",
            "createdAt, updatedAt",
        ],
        BLUE_FILL,
    )
    draw_entity_box(
        pdf,
        60,
        165,
        260,
        140,
        "PendingRegistration",
        [
            "PK id : UUID",
            "email, phoneNumber, username",
            "encodedPassword",
            "otp, expiresAt",
        ],
        SLATE_FILL,
    )
    draw_entity_box(
        pdf,
        60,
        25,
        260,
        120,
        "CreditTopupTransaction",
        [
            "PK id : Long",
            "orderId, paymentId, userId",
            "credits, source, createdAt",
        ],
        AMBER_FILL,
    )

    draw_entity_box(
        pdf,
        390,
        555,
        250,
        175,
        "Room",
        [
            "PK roomId : UUID",
            "name, roomType",
            "createdBy -> User.userId",
            "isPrivate, maxMembers",
            "description, avatarUrl, inviteCode",
            "createdAt, lastMessageAt",
        ],
        GREEN_FILL,
    )
    draw_entity_box(
        pdf,
        390,
        335,
        250,
        170,
        "RoomMember",
        [
            "PK id : UUID",
            "roomId -> Room.roomId",
            "userId -> User.userId",
            "role, joinedAt, lastReadAt",
            "muted",
        ],
        GREEN_FILL,
    )
    draw_entity_box(
        pdf,
        690,
        520,
        270,
        210,
        "Message",
        [
            "PK id : Long",
            "sender -> User.userId",
            "roomId -> Room.roomId",
            "content, messageType, timestamp",
            "attachmentName, attachmentPath, attachmentContentType",
            "replyToMessageId -> Message.id",
            "deleted, editedAt, deletedAt",
            "detectedLanguage, transcript, transcriptUpdatedAt",
        ],
        TEAL_FILL,
    )
    draw_entity_box(
        pdf,
        690,
        320,
        270,
        145,
        "MessageReaction",
        [
            "PK id : Long",
            "messageId -> Message.id",
            "roomId -> Room.roomId",
            "userId -> User.userId",
            "emoji, timestamp",
        ],
        TEAL_FILL,
    )

    draw_entity_box(
        pdf,
        1010,
        520,
        140,
        125,
        "Notification",
        [
            "PK id : Long",
            "userId -> User.userId",
            "message, read, timestamp",
        ],
        BLUE_FILL,
    )
    draw_entity_box(
        pdf,
        1010,
        320,
        140,
        190,
        "Payment",
        [
            "PK id : Long",
            "orderId, paymentId, signature",
            "amount, currency, planCode, planName",
            "status, credits",
            "userId -> User.userId",
            "customerName, customerEmail",
            "createdAt, verifiedAt, creditedAt",
        ],
        AMBER_FILL,
    )

    draw_relation(pdf, (320, 635), (390, 635), "1 user creates many rooms")
    draw_relation(pdf, (320, 420), (390, 420), "1 user owns many contacts")
    draw_relation(pdf, (320, 620), (690, 620), "1 user sends many messages")
    draw_relation(pdf, (320, 392), (390, 392), "1 user joins many rooms")
    draw_relation(pdf, (515, 555), (515, 505), "1 room has many members")
    draw_relation(pdf, (640, 605), (690, 605), "1 room has many messages")
    draw_relation(pdf, (825, 520), (825, 465), "1 message has many reactions")
    draw_relation(pdf, (320, 110), (1010, 400), "1 user makes many payments")
    draw_relation(pdf, (320, 90), (390, 90), "1 user receives many top-ups")
    draw_relation(pdf, (320, 575), (1010, 575), "1 user receives many notifications")
    draw_relation(pdf, (1120, 400), (320, 70), "orderId and userId feed credit history", color=AMBER, dashed=True)
    draw_relation(pdf, (190, 165), (190, 145), "OTP completion creates a user", color=HexColor("#64748B"), dashed=True)
    draw_relation(pdf, (320, 400), (690, 390), "1 user reacts many times")
    draw_relation(pdf, (515, 335), (690, 392), "room context", color=GREEN)
    draw_relation(pdf, (190, 350), (190, 540), "contactUserId may point to another user", color=HexColor("#64748B"), dashed=True)
    draw_relation(pdf, (825, 610), (825, 700), "replyToMessageId supports self reference", color=HexColor("#64748B"), dashed=True)

    draw_note_box(
        pdf,
        890,
        40,
        250,
        210,
        "Important modeling note",
        [
            "The backend is split into microservices, so most relationships are logical rather than database-level foreign keys.",
            "For example, message-service stores sender and roomId as plain values that reference auth-service and room-service records.",
            "That is why the ER view mixes direct table ownership with cross-service ID references.",
        ],
        fill_color=colors.white,
        stroke_color=LIGHT,
        title_color=TEXT,
    )

    draw_footer(pdf, "ER view: entity fields are summarized from the current JPA models in auth-service, room-service, message-service, notification-service, and payment-service.")
    pdf.showPage()


def generate_pdf(output_path: Path):
    output_path.parent.mkdir(parents=True, exist_ok=True)
    pdf = canvas.Canvas(str(output_path), pagesize=landscape(A3))
    pdf.setTitle("ConnectHub UML and ER Diagrams")
    pdf.setAuthor("OpenAI Codex")
    pdf.setSubject("ConnectHub backend architecture and entity diagrams")

    page_cover(pdf)
    page_deployment_uml(pdf)
    page_interaction_uml(pdf)
    page_er(pdf)

    pdf.save()


if __name__ == "__main__":
    target = Path(__file__).with_name("ConnectHub_UML_ER_Diagrams.pdf")
    generate_pdf(target)
    print(f"Created {target}")
