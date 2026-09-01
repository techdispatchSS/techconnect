package silver.solutions.techdispatch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A structured postal address: street, suburb, city, {@link Province} and postal code, each
 * its own column. For technicians this is the origin point for distance-based job matching
 * (PRD Phase 2), which is why every field is required — via {@code @NotBlank}/{@code @NotNull}
 * on {@code AddressRequest}, not a DB constraint — on every user created or edited through the
 * admin portal; a matching algorithm cannot work around a missing suburb or province the way a
 * human reading a free-text address could.
 *
 * <p>Columns themselves stay nullable: the bootstrap Manager (see {@code
 * BootstrapAdminRunner}) is created outside the normal onboarding flow and has no address at
 * all, and never will — only technicians are ever distance-matched.
 */
@Embeddable
@Getter
@Setter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
public class Address {

    @Column(name = "address_street")
    private String street;

    @Column(name = "address_suburb")
    private String suburb;

    @Column(name = "address_city")
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_province")
    private Province province;

    @Column(name = "address_postal_code")
    private String postalCode;

    public Address(String street, String suburb, String city, Province province, String postalCode) {
        this.street = street;
        this.suburb = suburb;
        this.city = city;
        this.province = province;
        this.postalCode = postalCode;
    }
}
