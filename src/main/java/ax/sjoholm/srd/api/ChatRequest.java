package ax.sjoholm.srd.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request payload for chat queries")
public class ChatRequest {

    @NotBlank(message = "Question is required")
    @Schema(description = "The question to ask", example = "Vad säger kommunallagen om beslutsfattande?")
    private String question;

    @Schema(description = "Whether to include raw document chunks in the response", defaultValue = "false")
    private Boolean includeChunks;
}
