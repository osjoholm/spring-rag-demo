package ax.sjoholm.srd.interfaces;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ax.sjoholm.srd.api.IngestionApi;
import ax.sjoholm.srd.services.ingestion.IngestionService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
@Slf4j
public class IngestionController implements IngestionApi {

    private final IngestionService ingestionService;

    public IngestionController(final IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Override
    public String createIngestion(@RequestBody String entity) {
        var result = ingestionService.ingestLagtingetDocuments();
        log.info("Ingestion result: {}", result);
        return "Ingestion created for: " + entity;
    }
}
