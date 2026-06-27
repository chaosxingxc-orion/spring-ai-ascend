package com.huawei.ascend.examples.workmate.myfiles;

import com.huawei.ascend.examples.workmate.myfiles.dto.MyFileFavoriteRequest;
import com.huawei.ascend.examples.workmate.myfiles.dto.MyFileMoveRequest;
import com.huawei.ascend.examples.workmate.myfiles.dto.MyFilePathRequest;
import com.huawei.ascend.examples.workmate.myfiles.dto.MyFileRenameRequest;
import com.huawei.ascend.examples.workmate.myfiles.dto.MyFileResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/files")
public class MyFilesController {

    private final MyFilesService myFilesService;

    public MyFilesController(MyFilesService myFilesService) {
        this.myFilesService = myFilesService;
    }

    @GetMapping
    public List<MyFileResponse> listFiles(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(defaultValue = "false") boolean favoritesOnly) {
        return myFilesService.list(q, sort, order, favoritesOnly);
    }

    @PostMapping("/rename")
    public MyFileResponse rename(@Valid @RequestBody MyFileRenameRequest request) {
        return myFilesService.rename(request.sessionId(), request.path(), request.newName());
    }

    @PostMapping("/move")
    public MyFileResponse move(@Valid @RequestBody MyFileMoveRequest request) {
        return myFilesService.move(request.sessionId(), request.path(), request.destPath());
    }

    @DeleteMapping
    public void delete(@Valid @RequestBody MyFilePathRequest request) {
        myFilesService.delete(request.sessionId(), request.path());
    }

    @PostMapping("/favorite")
    public MyFileResponse favorite(@Valid @RequestBody MyFileFavoriteRequest request) {
        return myFilesService.setFavorite(request.sessionId(), request.path(), request.favorite());
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> download(
            @RequestParam UUID sessionId, @RequestParam String path) {
        return myFilesService.download(sessionId, path);
    }
}
