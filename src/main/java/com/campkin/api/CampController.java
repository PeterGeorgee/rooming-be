package com.campkin.api;
import com.campkin.api.ApiModels.*; import com.campkin.domain.*; import com.campkin.repo.*; import com.campkin.service.*; import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import org.springframework.web.multipart.MultipartFile; import java.io.*; import java.time.*; import java.util.*;
@RestController @RequestMapping("/api") @RequiredArgsConstructor public class CampController {
 private final CampService service; private final ExcelImportService importer; private final AssignmentService assignments; private final PdfService pdf; private final PreferenceRepository preferences; private final CamperRepository campers;
 @GetMapping("/health") Map<String,String> health(){return Map.of("status","ok");}
 @GetMapping("/camps") List<Camp> camps(){return service.list();} @PostMapping("/camps") @ResponseStatus(HttpStatus.CREATED) Camp create(@Valid @RequestBody CampRequest r){return service.create(r);}
 @DeleteMapping("/camps/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteCamp(@PathVariable UUID id){service.deleteCamp(id);}
 @GetMapping("/camps/{id}/dashboard") Dashboard dashboard(@PathVariable UUID id){return service.dashboard(id);} @GetMapping("/camps/{id}/campers/search") List<CamperView> search(@PathVariable UUID id,@RequestParam String q){return service.search(id,q);}
 @PostMapping("/camps/{id}/rooms") @ResponseStatus(HttpStatus.CREATED) Room room(@PathVariable UUID id,@Valid @RequestBody RoomRequest r){return service.addRoom(id,r);}
 @PostMapping("/camps/{id}/rooms/batch") @ResponseStatus(HttpStatus.CREATED) List<Room> rooms(@PathVariable UUID id,@Valid @RequestBody BatchRoomRequest r){return service.addRooms(id,r);}
 @PatchMapping("/rooms/{id}") Room renameRoom(@PathVariable UUID id,@Valid @RequestBody RoomRenameRequest r){return service.renameRoom(id,r);}
 @PostMapping(value="/camps/{id}/import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) ImportResult importCampers(@PathVariable UUID id,@RequestPart MultipartFile file)throws IOException{return importer.importFile(id,file);}
 @PostMapping("/camps/{id}/assign/rooms") void assignRooms(@PathVariable UUID id,@Valid @RequestBody GenerateRoomsRequest r){assignments.assignRooms(id,r);} @PostMapping("/camps/{id}/assign/groups") void assignGroups(@PathVariable UUID id,@Valid @RequestBody GroupRequest r){assignments.assignGroups(id,r);}
 @PatchMapping("/campers/{id}/assignment") void move(@PathVariable UUID id,@RequestBody MoveRequest r){service.move(id,r);}
 @PatchMapping("/campers/{id}/gender") void gender(@PathVariable UUID id,@Valid @RequestBody GenderRequest r){service.updateGender(id,r);}
 @PatchMapping("/preferences/{id}/resolve") void resolve(@PathVariable UUID id,@Valid @RequestBody MatchRequest r){Preference p=preferences.findById(id).orElseThrow();Camper c=campers.findById(r.matchedCamperId()).orElseThrow();if(c.getCamp().getId().equals(p.getCamper().getCamp().getId())){p.setMatchedCamper(c);p.setStatus(Domain.PreferenceStatus.MATCHED);p.setSimilarity(1d);p.setAlternatives(null);}else throw new IllegalArgumentException("Camper belongs to another camp");}
 @GetMapping(value="/camps/{id}/exports/rooms.pdf",produces=MediaType.APPLICATION_PDF_VALUE) ResponseEntity<byte[]> roomPdf(@PathVariable UUID id){return download(pdf.rooms(id),"room-assignments.pdf");} @GetMapping(value="/camps/{id}/exports/groups.pdf",produces=MediaType.APPLICATION_PDF_VALUE) ResponseEntity<byte[]> groupPdf(@PathVariable UUID id){return download(pdf.groups(id),"discussion-groups.pdf");}
 private ResponseEntity<byte[]> download(byte[] bytes,String name){return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+name+"\"").body(bytes);}
}

