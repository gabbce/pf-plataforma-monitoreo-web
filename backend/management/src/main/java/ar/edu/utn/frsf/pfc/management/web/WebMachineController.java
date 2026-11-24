package ar.edu.utn.frsf.pfc.management.web;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = WebCookies.API + "/machines", produces = MediaType.APPLICATION_JSON_VALUE)
class WebMachineController {

    private final WebMachineCatalog catalog;

    WebMachineController(WebMachineCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    WebMachineCatalog.MachineList list(@AuthenticationPrincipal Long userId) {
        return catalog.list(userId);
    }

    @GetMapping("/{chassis}")
    WebMachineCatalog.MachineDetail detail(@AuthenticationPrincipal Long userId, @PathVariable String chassis) {
        return catalog.detail(userId, chassis);
    }
}
