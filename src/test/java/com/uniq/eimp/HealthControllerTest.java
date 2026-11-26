package com.uniq.eimp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(HealthController.class)
class HealthControllerTest {
 @Autowired MockMvc mvc;
 @Test void health() throws Exception {mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP")).andExpect(jsonPath("$.database").value("NOT_CONFIGURED"));}
 @Test void welcome() throws Exception {mvc.perform(get("/api/welcome")).andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("TRAINING"));}
}
