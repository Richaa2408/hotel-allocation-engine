package com.hotel.engine.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The React app handles its own routes (/reservations, /inventory, ...). A browser refresh on such a page
 * asks the server for that path, so it is answered with index.html. API, docs and files with an extension
 * are not matched.
 */
@Controller
public class SpaController {

    @GetMapping({
            "/{p1:(?!api$|v3$|swagger-ui$)[^.]*}",
            "/{p1:(?!api$|v3$|swagger-ui$)[^.]*}/{p2:[^.]*}",
            "/{p1:(?!api$|v3$|swagger-ui$)[^.]*}/{p2:[^.]*}/{p3:[^.]*}"})
    public String forward() {
        return "forward:/index.html";
    }
}
