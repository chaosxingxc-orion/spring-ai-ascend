package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.office.dto.ExpertImportRequest;
import com.huawei.ascend.examples.workmate.office.dto.ExpertSummaryResponse;
import com.huawei.ascend.examples.workmate.office.dto.ImportValidationResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/experts")
public class ExpertController {

    private final ExpertService expertService;
    private final ExpertImportService expertImportService;

    public ExpertController(ExpertService expertService, ExpertImportService expertImportService) {
        this.expertService = expertService;
        this.expertImportService = expertImportService;
    }

    @GetMapping
    public List<ExpertSummaryResponse> listExperts() {
        return expertService.listExperts();
    }

    @GetMapping("/{id}")
    public ExpertSummaryResponse getExpert(@PathVariable String id) {
        return expertService.getExpert(id);
    }

    @PostMapping("/import/validate")
    public ImportValidationResponse validateImport(@RequestBody ExpertImportRequest request) {
        return expertImportService.validate(request);
    }

    @PostMapping("/import")
    public ExpertSummaryResponse importExpert(@RequestBody ExpertImportRequest request) {
        return expertImportService.importExpert(request);
    }

    @PostMapping(value = "/import/zip", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExpertSummaryResponse importExpertZip(@RequestPart("file") MultipartFile file) throws java.io.IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Zip file is required");
        }
        return expertImportService.importExpertZip(file.getInputStream());
    }
}
