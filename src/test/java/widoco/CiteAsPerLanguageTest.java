package widoco;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import widoco.entities.Ontology;

/**
 * Tests for the per-language citation handling (EDINT extension).
 */
public class CiteAsPerLanguageTest {

    @Test
    public void citationIsResolvedPerLanguage() {
        Ontology o = new Ontology();
        o.setCiteAs("es", "Cita en español");
        o.setCiteAs("en", "Citation in English");

        assertEquals("Cita en español", o.getCiteAs("es"));
        assertEquals("Citation in English", o.getCiteAs("en"));
        // unknown language: falls back to any declared citation
        assertEquals("Cita en español", o.getCiteAs("fr"));
        assertEquals("Cita en español", o.getCiteAs());
    }

    @Test
    public void languageAgnosticCitationIsTheFallback() {
        Ontology o = new Ontology();
        o.setCiteAs("", "Global citation");
        o.setCiteAs("en", "Citation in English");

        assertEquals("Global citation", o.getCiteAs("es"));
        assertEquals("Citation in English", o.getCiteAs("en"));
        assertEquals("Global citation", o.getCiteAs());
    }

    @Test
    public void languageAgnosticSetterKeepsWorking() {
        Ontology o = new Ontology();
        o.setCiteAs("Solo una cita");
        assertEquals("Solo una cita", o.getCiteAs("es"));
        assertEquals("Solo una cita", o.getCiteAs("en"));
    }

    @Test
    public void noCitationReturnsNull() {
        Ontology o = new Ontology();
        assertNull(o.getCiteAs("es"));
        assertNull(o.getCiteAs());
    }

    @Test
    public void configurationCitationIsLowerPrecedenceThanAnyAnnotation() {
        Ontology o = new Ontology();
        o.setCiteAs(Ontology.CITE_AS_FROM_CONF, "Cita del conf");
        o.setCiteAs("en", "Citation in English");

        // no annotation for the requested language, no language-agnostic one
        assertEquals("Cita del conf", o.getCiteAs("es"));
        // annotation for the requested language wins
        assertEquals("Citation in English", o.getCiteAs("en"));
    }

    @Test
    public void nullValuesAreIgnored() {
        Ontology o = new Ontology();
        o.setCiteAs("es", null);
        assertNull(o.getCiteAs("es"));
    }
}
