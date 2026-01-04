package ax.sjoholm.srd.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import io.swagger.v3.oas.annotations.tags.Tag;
@Tag(name = "Ingestion", description = "API for document ingestion")
public interface IngestionApi {
    @PostMapping("/ingestions")
    public String createIngestion(@RequestBody String entity);
}
