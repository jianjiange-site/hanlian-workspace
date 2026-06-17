package com.jianjiange.dating.post.controller;

import com.jianjiange.dating.post.service.PostWriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/debug/posts")
@RequiredArgsConstructor
public class PostDebugController {

    private final PostWriteService postWriteService;

    @PostMapping
    public CreatePostDebugResponse createPost(@RequestBody CreatePostDebugRequest request) {
        Long postId = postWriteService.createPost(
                request.userId(),
                request.content(),
                request.imageKeys() == null ? List.of() : request.imageKeys()
        );

        return new CreatePostDebugResponse(postId);
    }

    public record CreatePostDebugRequest(
            Long userId,
            String content,
            List<String> imageKeys
    ) {
    }

    public record CreatePostDebugResponse(
            Long postId
    ) {
    }
}
