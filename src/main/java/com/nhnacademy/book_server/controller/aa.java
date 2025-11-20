//package com.nhnacademy.book_server.controller;
//import io.swagger.v3.oas.annotations.Operation;
//import io.swagger.v3.oas.annotations.Parameter;
//import io.swagger.v3.oas.annotations.media.Content;
//import io.swagger.v3.oas.annotations.media.Schema;
//import io.swagger.v3.oas.annotations.parameters.RequestBody;
//import io.swagger.v3.oas.annotations.responses.ApiResponse;
//import io.swagger.v3.oas.annotations.responses.ApiResponses;
//import io.swagger.v3.oas.annotations.tags.Tag;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//
//@Tag(name = "Comment", description = "댓글 CRUD API")
//public interface CommentDocs {
//
//    @Operation(summary = "댓글 생성", description = "특정 업무에 새 댓글을 생성합니다. (Access: Project Member)")
//    @ApiResponses(value = {
//            @ApiResponse(responseCode = "201", description = "댓글 생성 성공"),
//            @ApiResponse(responseCode = "403", description = "권한 없음 (Not Project Member)"),
//            @ApiResponse(responseCode = "404", description = "프로젝트 또는 업무를 찾을 수 없음"),
//    })
//    @PostMapping
//    ResponseEntity<Void> createComment(
//            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
//            @PathVariable Long projectId,
//            @PathVariable Long taskId,
//            @RequestBody(description = "댓글 내용", required = true, content = @Content(schema = @Schema(implementation = CommentRequest.class)))
//            @org.springframework.web.bind.annotation.RequestBody CommentRequest request);
//
//
////    @Operation(summary = "댓글 목록 조회", description = "특정 업무의 댓글 목록을 조회합니다. (Access: Project Member)")
////    @ApiResponses(value = {
////            @ApiResponse(responseCode = "200", description = "댓글 목록 조회 성공",
////                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CommentResponse.class))),
////            @ApiResponse(responseCode = "403", description = "권한 없음"),
////            @ApiResponse(responseCode = "404", description = "프로젝트 또는 업무를 찾을 수 없음"),
////    })
////    @GetMapping
////    ResponseEntity<List<CommentResponse>> getComments(
////            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
////            @PathVariable Long projectId,
////            @PathVariable Long taskId);
////
////    @Operation(summary = "댓글 수정", description = "특정 댓글의 내용을 수정합니다. (Access: Comment Creator)")
////    @ApiResponses(value = {
////            @ApiResponse(responseCode = "200", description = "댓글 수정 성공"),
////            @ApiResponse(responseCode = "403", description = "권한 없음 (Not Comment Creator)"),
////            @ApiResponse(responseCode = "404", description = "댓글을 찾을 수 없음"),
////    })
////    @PutMapping("/{commentId}")
////    ResponseEntity<Void> updateComment(
////            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
////            @PathVariable Long projectId,
////            @PathVariable Long taskId,
////            @PathVariable Long commentId,
////            @RequestBody(description = "수정할 댓글 내용", required = true, content = @Content(schema = @Schema(implementation = CommentRequest.class)))
////            @org.springframework.web.bind.annotation.RequestBody CommentRequest request);
////
//    @Operation(summary = "댓글 삭제", description = "특정 댓글을 삭제합니다. (Access: Comment Creator)")
//    @ApiResponses(value = {
//            @ApiResponse(responseCode = "204", description = "댓글 삭제 성공"),
//            @ApiResponse(responseCode = "403", description = "권한 없음"),
//            @ApiResponse(responseCode = "404", description = "댓글을 찾을 수 없음"),
//    })
////    @DeleteMapping("/{commentId}")
////    ResponseEntity<Void> deleteComment(
////            @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId,
////            @PathVariable Long projectId,
////            @PathVariable Long taskId,
////            @PathVariable Long commentId);
////}
////
////----------------controller-------------------------------
////
////        package com.nhnacademy.springbootjpa.controller;
////
////import com.nhnacademy.springbootjpa.controller.apidocs.CommentDocs;
////import com.nhnacademy.springbootjpa.domain.Comment;
////import com.nhnacademy.springbootjpa.dto.comment.CommentRequest;
////import com.nhnacademy.springbootjpa.dto.comment.CommentResponse;
////import com.nhnacademy.springbootjpa.service.CommentService;
////import lombok.RequiredArgsConstructor;
////import org.springframework.http.ResponseEntity;
////import org.springframework.web.bind.annotation.*;
////
////        import java.util.List;
////
////@RestController
////@RequiredArgsConstructor
////@RequestMapping("/projects/{projectId}/tasks/{taskId}/comments")
////public class CommentController implements CommentDocs {
////
////    private final CommentService commentService;
////
////    @PostMapping
////    public ResponseEntity<Void> createComment(@RequestHeader("X-User-Id") Long userId,
////                                              @PathVariable Long projectId,
////                                              @PathVariable Long taskId,
////                                              @RequestBody CommentRequest request) {
////        commentService.createComment(userId,projectId, taskId, request.getContent());
////
////        return ResponseEntity.status(201).build();
////    }
////
////    @GetMapping
////    public ResponseEntity<List<CommentResponse>> getComments(@RequestHeader("X-User-Id") Long userId,
////                                                             @PathVariable Long projectId,
////                                                             @PathVariable Long taskId) {
////        List<Comment> comments = commentService.getComments(userId, projectId, taskId);
////        List<CommentResponse> list = comments.stream().map(CommentResponse::formEntity).toList();
////        return ResponseEntity.status(200).body(list);
////    }
////
////    @PutMapping("/{commentId}")
////    public ResponseEntity<Void> updateComment(@RequestHeader("X-User-Id") Long userId,
////                                              @PathVariable Long projectId,
////                                              @PathVariable Long taskId,
////                                              @PathVariable Long commentId,
////                                              @RequestBody CommentRequest request) {
////        commentService.updateComment(userId, commentId, request.getContent(),taskId);
////        return ResponseEntity.status(200).build();
////    }
////
////    @DeleteMapping("/{commentId}")
////    public ResponseEntity<Void> deleteComment(@RequestHeader("X-User-Id") Long userId,
////                                              @PathVariable Long projectId,
////                                              @PathVariable Long taskId,
////                                              @PathVariable Long commentId) {
////        commentService.deleteComment(userId, commentId, taskId);
////        return ResponseEntity.status(204).build();
//    }
//}