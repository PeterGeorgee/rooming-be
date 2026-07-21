package com.campkin.service;

import com.campkin.api.ApiModels.*;
import com.campkin.domain.Camp;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PdfService {
    private static final Color DARK = new Color(21, 61, 57);
    private static final Color TEAL = new Color(29, 98, 93);
    private static final Color PALE = new Color(232, 240, 237);
    private static final Color LINE = new Color(211, 222, 218);
    private static final Color MUTED = new Color(91, 111, 107);
    private final CampService service;

    public byte[] rooms(UUID campId) { return render(campId, true); }
    public byte[] groups(UUID campId) { return render(campId, false); }

    private byte[] render(UUID campId, boolean roomMode) {
        Dashboard dashboard = service.dashboard(campId);
        Camp camp = (Camp) dashboard.camp();
        String reportName = roomMode ? "Room Assignments" : "Discussion Groups";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 34, 34, 42, 42);
        PdfWriter writer = PdfWriter.getInstance(document, output);
        writer.setPageEvent(new Footer(camp.getName()));
        document.open();

        DateTimeFormatter date = DateTimeFormatter.ofPattern("d MMM yyyy");
        if (roomMode) {
            List<RoomView> occupiedRooms = dashboard.rooms().stream().filter(r -> r.occupancy() > 0).toList();
            for (int i = 0; i < occupiedRooms.size(); i++) {
                if (i > 0) document.newPage();
                addReportHeader(document, camp, reportName, date);
                document.add(roomSection(occupiedRooms.get(i)));
            }
        } else {
            addReportHeader(document, camp, reportName, date);
            for (GroupView group : dashboard.groups()) document.add(groupSection(group));
        }
        document.close();
        return output.toByteArray();
    }

    private void addReportHeader(Document document, Camp camp, String reportName, DateTimeFormatter date) {
        Paragraph campTitle = new Paragraph(camp.getName(), font(22, Font.BOLD, DARK));
        campTitle.setSpacingAfter(3);
        document.add(campTitle);
        Paragraph reportTitle = new Paragraph(reportName, font(13, Font.BOLD, TEAL));
        reportTitle.setSpacingAfter(4);
        document.add(reportTitle);
        document.add(new Paragraph(camp.getStartDate().format(date) + " - " + camp.getEndDate().format(date) + "   |   Generated " + LocalDate.now().format(date), font(8, Font.NORMAL, MUTED)));
        document.add(spacer(10));
    }

    private PdfPTable roomSection(RoomView room) {
        PdfPTable section = sectionTable();
        section.addCell(sectionHeader(room.name()));
        String meta = (room.gender().name().equals("FEMALE") ? "Girls" : "Boys") + "   |   Beds used: " + room.occupancy() + " / " + room.capacity();
        if (!room.leaders().isEmpty()) meta += "   |   Leaders: " + room.leaders().stream().map(l -> l.name() + " (sleeps in " + l.sleepRoom() + ")").reduce((a,b) -> a + ", " + b).orElse("");
        section.addCell(metaCell(meta));
        section.addCell(wrapped(roomMemberTable(room.campers())));
        return section;
    }

    private PdfPTable groupSection(GroupView group) {
        PdfPTable section = sectionTable();
        section.addCell(sectionHeader(group.name()));
        String meta = group.occupancy() + " campers   |   Average age: " + group.averageAge();
        if (!group.leaders().isEmpty()) meta += "   |   Leaders: " + String.join(", ", group.leaders());
        section.addCell(metaCell(meta));
        section.addCell(wrapped(memberTable(group.campers())));
        return section;
    }

    private PdfPTable sectionTable() {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingAfter(12);
        table.setKeepTogether(true);
        return table;
    }

    private PdfPCell sectionHeader(String name) {
        PdfPCell cell = new PdfPCell(new Phrase(name, font(12, Font.BOLD, Color.WHITE)));
        cell.setBackgroundColor(DARK); cell.setBorderColor(DARK); cell.setPadding(8);
        return cell;
    }

    private PdfPCell metaCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font(8, Font.BOLD, TEAL)));
        cell.setBackgroundColor(PALE); cell.setBorderColor(LINE); cell.setPadding(6);
        return cell;
    }

    private PdfPCell wrapped(PdfPTable table) {
        PdfPCell cell = new PdfPCell(table);
        cell.setBorderColor(LINE); cell.setPadding(0);
        return cell;
    }

    private PdfPTable memberTable(List<CamperView> campers) {
        PdfPTable table = new PdfPTable(new float[]{4.8f, 1.2f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String heading : List.of("Camper", "Age")) {
            PdfPCell cell = new PdfPCell(new Phrase(heading, font(8, Font.BOLD, DARK)));
            cell.setBackgroundColor(new Color(247, 250, 248)); cell.setBorderColor(LINE); cell.setPadding(6);
            table.addCell(cell);
        }
        if (campers.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("No campers assigned", font(8, Font.ITALIC, MUTED)));
            empty.setColspan(2); empty.setBorderColor(LINE); empty.setPadding(7); table.addCell(empty);
        }
        for (CamperView camper : campers) {
            table.addCell(bodyCell(camper.name()));
            table.addCell(bodyCell(String.valueOf(camper.age())));
        }
        return table;
    }

    private PdfPTable roomMemberTable(List<CamperView> campers) {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        PdfPCell heading = new PdfPCell(new Phrase("Camper", font(8, Font.BOLD, DARK)));
        heading.setBackgroundColor(new Color(247, 250, 248));
        heading.setBorderColor(LINE);
        heading.setPadding(6);
        table.addCell(heading);
        if (campers.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("No campers assigned", font(8, Font.ITALIC, MUTED)));
            empty.setBorderColor(LINE);
            empty.setPadding(7);
            table.addCell(empty);
        }
        for (CamperView camper : campers) table.addCell(bodyCell(camper.name()));
        return table;
    }

    private PdfPCell bodyCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font(8, Font.NORMAL, Color.BLACK)));
        cell.setBorderColor(LINE); cell.setPadding(6); cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    private Paragraph spacer(float space) { Paragraph p = new Paragraph(" "); p.setSpacingAfter(space); return p; }
    private Font font(float size, int style, Color color) { return new Font(Font.HELVETICA, size, style, color); }

    static class Footer extends PdfPageEventHelper {
        private final String campName;
        Footer(String campName) { this.campName = campName; }
        @Override public void onEndPage(PdfWriter writer, Document document) {
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_LEFT, new Phrase(campName, new Font(Font.HELVETICA, 7, Font.NORMAL, MUTED)), document.left(), document.bottom() - 18, 0);
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_RIGHT, new Phrase("Page " + writer.getPageNumber(), new Font(Font.HELVETICA, 7, Font.NORMAL, MUTED)), document.right(), document.bottom() - 18, 0);
        }
    }
}
