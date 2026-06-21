package fu.osms.address.controller;

import fu.osms.address.dto.CountryDto;
import fu.osms.address.entity.AdministrativeDivision;
import fu.osms.address.service.AddressService;
import fu.osms.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping("/countries")
    public ResponseEntity<ApiResponse<List<CountryDto>>> getCountries() {
        List<CountryDto> countries = addressService.getAllCountries();
        return ResponseEntity.ok(ApiResponse.success(countries));
    }

    @GetMapping("/divisions")
    public ResponseEntity<ApiResponse<List<AdministrativeDivision>>> getDivisions(
            @RequestParam String country,
            @RequestParam Integer level,
            @RequestParam(required = false) String parent) {
        List<AdministrativeDivision> divisions = addressService.getDivisions(country, level, parent);
        return ResponseEntity.ok(ApiResponse.success(divisions));
    }
}
