package silver.solutions.techconnect.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * The countries the app is used in: the 54 states of Africa, keyed by their ISO 3166-1 alpha-2
 * code. Each constant is named by its code so it is stored and sent as {@code "KE"}, {@code "ZA"}.
 *
 * <p>The international dialling code is what lets a phone number typed the local way (a leading
 * 0) be recognised as the same number as its international form.
 */
public enum Country {
    DZ("Algeria", "213"),
    AO("Angola", "244"),
    BJ("Benin", "229"),
    BW("Botswana", "267"),
    BF("Burkina Faso", "226"),
    BI("Burundi", "257"),
    CV("Cabo Verde", "238"),
    CM("Cameroon", "237"),
    CF("Central African Republic", "236"),
    TD("Chad", "235"),
    KM("Comoros", "269"),
    CG("Congo", "242"),
    CD("Democratic Republic of the Congo", "243"),
    CI("Côte d'Ivoire", "225"),
    DJ("Djibouti", "253"),
    EG("Egypt", "20"),
    GQ("Equatorial Guinea", "240"),
    ER("Eritrea", "291"),
    SZ("Eswatini", "268"),
    ET("Ethiopia", "251"),
    GA("Gabon", "241"),
    GM("Gambia", "220"),
    GH("Ghana", "233"),
    GN("Guinea", "224"),
    GW("Guinea-Bissau", "245"),
    KE("Kenya", "254"),
    LS("Lesotho", "266"),
    LR("Liberia", "231"),
    LY("Libya", "218"),
    MG("Madagascar", "261"),
    MW("Malawi", "265"),
    ML("Mali", "223"),
    MR("Mauritania", "222"),
    MU("Mauritius", "230"),
    MA("Morocco", "212"),
    MZ("Mozambique", "258"),
    NA("Namibia", "264"),
    NE("Niger", "227"),
    NG("Nigeria", "234"),
    RW("Rwanda", "250"),
    ST("São Tomé and Príncipe", "239"),
    SN("Senegal", "221"),
    SC("Seychelles", "248"),
    SL("Sierra Leone", "232"),
    SO("Somalia", "252"),
    ZA("South Africa", "27"),
    SS("South Sudan", "211"),
    SD("Sudan", "249"),
    TZ("Tanzania", "255"),
    TG("Togo", "228"),
    TN("Tunisia", "216"),
    UG("Uganda", "256"),
    ZM("Zambia", "260"),
    ZW("Zimbabwe", "263");

    private final String displayName;
    private final String dialCode;

    Country(String displayName, String dialCode) {
        this.displayName = displayName;
        this.dialCode = dialCode;
    }

    public String displayName() {
        return displayName;
    }

    /** The international dialling code without the plus, e.g. {@code "254"}. */
    public String dialCode() {
        return dialCode;
    }

    /** What an address with no country is taken to be in: everything recorded before countries. */
    public static final Country DEFAULT = ZA;

    public static Optional<Country> fromCode(String code) {
        return Arrays.stream(values()).filter(c -> c.name().equalsIgnoreCase(code)).findFirst();
    }
}
