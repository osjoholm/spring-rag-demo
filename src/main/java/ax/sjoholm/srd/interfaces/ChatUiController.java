package ax.sjoholm.srd.interfaces;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Simple controller to serve the chat UI page.
 */
@Controller
public class ChatUiController {

  @GetMapping("/chat")
  public String chatPage() {
    return "chat";
  }

}