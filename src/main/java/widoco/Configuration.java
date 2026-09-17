/*
 * Copyright 2012-2013 Ontology Engineering Group, Universidad Politecnica de Madrid, Spain
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package widoco;

import java.awt.Image;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.rdf.rdfxml.renderer.OWLOntologyXMLNamespaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import widoco.entities.Agent;
import widoco.entities.License;
import widoco.entities.Ontology;
import widoco.gui.GuiController;
import licensius.GetLicense;

/**
 * class for storing all the details to generate the ontology. This will be a
 * singleton object that will be modified until the generate command is given.
 * 
 * @author Daniel Garijo
 */
public class Configuration {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	private Ontology mainOntologyMetadata;
	/**
	 * Path where the ontology is saved (locally)
	 */
	private String ontologyPath;
	/**
	 * Path where the documentation will be generated.
	 */
	private String documentationURI;
	/**
	 * Flag to tell Widoco whether the ontology will be loaded from a file or URI.
	 * True if loaded from file.
	 */
	private boolean fromFile;

	private boolean publishProvenance;

	private boolean includeAbstract;
	private boolean includeIntroduction;
	private boolean includeOverview;
	private boolean includeDescription;
	private boolean includeReferences;
	private boolean includeCrossReferenceSection;// needed for skeleton
	private boolean includeAnnotationProperties;
	private boolean includeNamedIndividuals;
	private boolean includeIndex;
	private boolean includeChangeLog;
	private String abstractPath;
	private String introductionPath;
	private String overviewPath;
	private String descriptionPath;
	private String referencesPath;
	// EDINT extension: per-language section content and paths (keys like "abstract-en", "pathToDescription-es")
	private final Map<String, String> abstractSectionByLang = new HashMap<>();
	private final Map<String, String> descriptionSectionByLang = new HashMap<>();
	private final Map<String, String> referencesSectionByLang = new HashMap<>();
	private final Map<String, String> introductionSectionByLang = new HashMap<>();
	private final Map<String, String> abstractPathByLang = new HashMap<>();
	private final Map<String, String> descriptionPathByLang = new HashMap<>();
	private final Map<String, String> referencesPathByLang = new HashMap<>();
	// EDINT extension: extra CSS/JS resources to include in the generated HTML head
	private final List<String> extraCSS = new ArrayList<>();
	private final List<String> extraJS = new ArrayList<>();
	// EDINT extension: extra files/dirs to copy into the output resources folder
	private final List<String> extraResources = new ArrayList<>();
	// EDINT extension: skip copying the generic readme.md into the output folder
	private boolean omitReadme = false;
	// EDINT extension: SKOS thesaurus HTML files (relative links for the index metadata)
	private final List<String> kosHTML = new ArrayList<>();
	// EDINT extension: vocabularies reused without owl:imports (detected from the model)
	private final List<Ontology> reusedVocabularies = new ArrayList<>();
	// EDINT extension: raw widoco:* annotation values with vocabulary labels
	private final Map<String, String> widocoAnnotationValues = new HashMap<>();
	private String googleAnalyticsCode = null;
	private String contextURI; // not added with an ontology because it's independent

	/**
	 * Property for including an ontology diagram (future work)
	 */
	private boolean includeDiagram;

	private Properties propertyFile;

	// Lode configuration parameters
	private boolean useOwlAPI;
	private boolean useImported;
	private boolean useReasoner;
	private String currentLanguage;
	private HashMap<String, Boolean> languages; // <language,the doc been generated for that lang or not>

	private Image logo;
	private Image logoMini;

	// for overwriting
	private boolean overwriteAll = false;

	// just in case there is an abstract included
	private String abstractSection;

	private boolean useW3CStyle;

	private String error;// Latest error to show to user via interface.

	// add imported ontologies in the doc as well
	private boolean addImportedOntologies;
	private File tmpFolder; // file where different auxiliary resources might be copied to
	private boolean createHTACCESS;
	private boolean createWebVowlVisualization;
	private boolean useLicensius;// optional usage of Licensius service.
	private boolean displaySerializations;// in case someone does not want serializations in their page
	private boolean displayDirectImportsOnly;// in case someone wants only the direct imports on their page
	private String rewriteBase;// rewrite base path for content negotiation (.htaccess)
    private boolean includeAllSectionsInOneDocument; //boolean to indicate all sections should be included in a single big HTML
	private HashMap<String, String> namespaceDeclarations; //Namespace declarations to be included in the documentation.
	private String introText;// in case there is an explicit annotation in the ontology
	private List<File> imports = new ArrayList<>();

	/**
	 * Variable to keep track of possible errors in the changelog. If there are
	 * errors, the section will not be produced. True by default.
	 */
	private boolean changeLogSuccessfullyCreated = true;

	public Configuration() {
		initializeConfig();
		try {
			// create a temporal folder with all LODE resources
			tmpFolder = new File("tmp" + new Date().getTime());
			tmpFolder.mkdir();
			WidocoUtils.copyResourceDir(Constants.LODE_PATH, tmpFolder);
		} catch (Exception ex) {
			logger.error("Error while creating the temporal file for storing the intermediate Widoco files.");
		}
		propertyFile = new Properties();
		// just in case, we initialize the objects:
		try {
			URL root = GuiController.class.getProtectionDomain().getCodeSource().getLocation();
			String path = (new File(root.toURI())).getParentFile().getPath();
			loadPropertyFile(path + File.separator + Constants.CONFIG_PATH);
		} catch (URISyntaxException | IOException e) {
			logger.warn("Error while loading the default property file: " + e.getMessage());
		}
	}

	public File getTmpFile() {
		return tmpFolder;
	}

	public final void initializeConfig() {
		// initialization of variables (in case something fails)
		abstractSection = "";
		publishProvenance = true;
		includeAbstract = true;
		includeIntroduction = true;
		includeOverview = true;
		includeDescription = true;
		includeReferences = true;
		includeCrossReferenceSection = true;
		includeAnnotationProperties = false;
		includeNamedIndividuals = true;
		includeIndex = true;
		includeChangeLog = true;
		if (languages == null) {
			languages = new HashMap<String, Boolean>();
		}
		currentLanguage = "en";
		languages.put("en", false);
		useW3CStyle = true;// by default
		error = "";
		addImportedOntologies = false;
		createHTACCESS = false;
		useLicensius = false;
		displaySerializations = true;
		displayDirectImportsOnly = false;
		rewriteBase = "/";
		contextURI = "";
		includeAllSectionsInOneDocument = false;
		introText = "";
		initializeOntology();
	}

	private void initializeOntology() {
		// this has to be checked because we might delete the uri of the onto from a
		// previous step.
		if (mainOntologyMetadata == null) {
			mainOntologyMetadata = new Ontology();
			mainOntologyMetadata.setNamespaceURI("");
		}
		mainOntologyMetadata.setTitle("");
		mainOntologyMetadata.setCreationDate("");
		mainOntologyMetadata.setModifiedDate("");
		mainOntologyMetadata.setPreviousVersion("");
		mainOntologyMetadata.setThisVersion("");
		mainOntologyMetadata.setLatestVersion("");
		mainOntologyMetadata.setRevision("");
		mainOntologyMetadata.setPublisher(new Agent());
		mainOntologyMetadata.setImportedOntologies(new ArrayList<>());
		mainOntologyMetadata.setExtendedOntologies(new ArrayList<>());
		mainOntologyMetadata.setName("");
		mainOntologyMetadata.setNamespacePrefix("");
		License l = new License();
		mainOntologyMetadata.setLicense(l);
		mainOntologyMetadata.setSerializations(new HashMap<>());
		// add default serializations: rdf/xml, n3, turtle and json-ld
		mainOntologyMetadata.addSerialization(Constants.RDF_XML, "ontology.owl");
		mainOntologyMetadata.addSerialization(Constants.TTL, "ontology.ttl");
		mainOntologyMetadata.addSerialization(Constants.NT, "ontology.nt");
		mainOntologyMetadata.addSerialization(Constants.JSON_LD, "ontology.jsonld");
		mainOntologyMetadata.setCreators(new ArrayList<>());
		mainOntologyMetadata.setContributors(new ArrayList<>());
		mainOntologyMetadata.setCiteAs("");
		mainOntologyMetadata.setDoi("");
		mainOntologyMetadata.setStatus("");
		mainOntologyMetadata.setLogo("");
		mainOntologyMetadata.setDescription("");
		mainOntologyMetadata.setBackwardsCompatibleWith("");
		mainOntologyMetadata.setIncompatibleWith("");
		mainOntologyMetadata.setImages(new ArrayList<>());
		mainOntologyMetadata.setSources(new ArrayList<>());
		mainOntologyMetadata.setSeeAlso(new ArrayList<>());
		mainOntologyMetadata.setFunders(new ArrayList<>());
		mainOntologyMetadata.setFundingGrants(new ArrayList<>());
		mainOntologyMetadata.setCodeRepository("");
		this.namespaceDeclarations = new HashMap<>();
	}

	private void loadPropertyFile(String path) throws IOException {
		try {
			initializeOntology();
			// this forces the property file to be in UTF 8 instead of the ISO
			propertyFile.load(new InputStreamReader(new FileInputStream(path), "UTF-8"));
			// We try to load from the configuration file. If it fails, then we should try
			// to load from the ontology. Then, if it fails, we should ask the user.
			abstractSection = propertyFile.getProperty(Constants.PF_ABSTRACT_SECTION_CONTENT);
			contextURI = propertyFile.getProperty(Constants.PF_CONTEXT_URI, "");
			mainOntologyMetadata.setTitle(propertyFile.getProperty(Constants.PF_ONT_TITLE, "Title goes here"));
			mainOntologyMetadata.setCreationDate(propertyFile.getProperty(Constants.PF_DATE_CREATED, "Date created"));
			mainOntologyMetadata.setModifiedDate(propertyFile.getProperty(Constants.PF_DATE_MODIFIED, "Date modified"));
			mainOntologyMetadata.setIssuedDate(propertyFile.getProperty(Constants.PF_DATE_ISSUED, "Date issued"));
			mainOntologyMetadata.setPreviousVersion(propertyFile.getProperty(Constants.PF_PREVIOUS_VERSION));
			mainOntologyMetadata.setThisVersion(propertyFile.getProperty(Constants.PF_THIS_VERSION_URI));
			mainOntologyMetadata.setLatestVersion(propertyFile.getProperty(Constants.PF_LATEST_VERSION_URI));
			mainOntologyMetadata.setName(propertyFile.getProperty(Constants.PF_ONT_NAME));
			mainOntologyMetadata.setNamespacePrefix(propertyFile.getProperty(Constants.PF_ONT_PREFIX));
			mainOntologyMetadata.setNamespaceURI(propertyFile.getProperty(Constants.PF_ONT_NAMESPACE_URI));
			mainOntologyMetadata.setRevision(propertyFile.getProperty(Constants.PF_ONT_REVISION_NUMBER));
			Agent publisher = new Agent();
			publisher.setName(propertyFile.getProperty(Constants.PF_PUBLISHER, ""));
			publisher.setURL(propertyFile.getProperty(Constants.PF_PUBLISHER_URI, ""));
			publisher.setInstitutionName(propertyFile.getProperty(Constants.PF_PUBLISHER_INSTITUTION, ""));
			publisher.setInstitutionURL(propertyFile.getProperty(Constants.PF_PUBLISHER_INSTITUTION_URI, ""));
			mainOntologyMetadata.setPublisher(publisher);
			String aux = propertyFile.getProperty(Constants.PF_AUTHORS, "");
			String[] names, urls, authorInst, authorInstURI;
			if (!aux.isEmpty()) {
				names = aux.split(";");
				aux = propertyFile.getProperty(Constants.PF_AUTHORS_URI, "");
				urls = aux.split(";");
				aux = propertyFile.getProperty(Constants.PF_AUTHORS_INSTITUTION, "");
				authorInst = aux.split(";");
				aux = propertyFile.getProperty(Constants.PF_AUTHORS_INSTITUTION_URI, "");
				authorInstURI = aux.split(";");
				for (int i = 0; i < names.length; i++) {
					Agent a = new Agent();
					a.setName(names[i]);
					try {
						a.setURL(urls[i]);
					} catch (Exception e) {
						a.setURL("");
					}
					try {
						a.setInstitutionName(authorInst[i]);
					} catch (Exception e) {
						a.setInstitutionName("");
					}
					try {
						a.setInstitutionURL(authorInstURI[i]);
					} catch (Exception e) {
						a.setInstitutionURL("");
					}
					mainOntologyMetadata.getCreators().add(a);
				}
			}
			aux = propertyFile.getProperty(Constants.PF_CONTRIBUTORS, "");
			if (!aux.isEmpty()) {
				names = aux.split(";");
				aux = propertyFile.getProperty(Constants.PF_CONTRIBUTORS_URI, "");
				urls = aux.split(";");
				aux = propertyFile.getProperty(Constants.PF_CONTRIBUTORS_INSTITUTION, "");
				authorInst = aux.split(";");
				aux = propertyFile.getProperty(Constants.PF_CONTRIBUTORS_INSTITUTION_URI, "");
				authorInstURI = aux.split(";");
				for (int i = 0; i < names.length; i++) {
					Agent a = new Agent();
					a.setName(names[i]);
					try {
						a.setURL(urls[i]);
					} catch (Exception e) {
						a.setURL("");
					}
					try {
						a.setInstitutionName(authorInst[i]);
					} catch (Exception e) {
						a.setInstitutionName("");
					}
					try {
						a.setInstitutionURL(authorInstURI[i]);
					} catch (Exception e) {
						a.setInstitutionURL("");
					}
					mainOntologyMetadata.getContributors().add(a);
				}
			}
			aux = propertyFile.getProperty(Constants.PF_IMPORTED_ONTOLOGY_NAMES, "");
			names = aux.split(";");
			aux = propertyFile.getProperty(Constants.PF_IMPORTED_ONTOLOGY_URIS, "");
			urls = aux.split(";");
			for (int i = 0; i < names.length; i++) {
				if (!"".equals(names[i])) {
					Ontology o = new Ontology();
					o.setName(names[i]);
					try {
						o.setNamespaceURI(urls[i]);
					} catch (Exception e) {
						o.setNamespaceURI("");
					}
					mainOntologyMetadata.getImportedOntologies().add(o);
				}
			}
			aux = propertyFile.getProperty(Constants.PF_EXTENDED_ONTOLOGY_NAMES, "");
			names = aux.split(";");
			aux = propertyFile.getProperty(Constants.PF_EXTENDED_ONTOLOGY_URIS, "");
			urls = aux.split(";");
			for (int i = 0; i < names.length; i++) {
				if (!"".equals(names[i])) {
					Ontology o = new Ontology();
					o.setName(names[i]);
					try {
						o.setNamespaceURI(urls[i]);
					} catch (Exception e) {
						o.setNamespaceURI("");
					}
					mainOntologyMetadata.getExtendedOntologies().add(o);
				}
			}
			mainOntologyMetadata.getLicense().setName(propertyFile.getProperty(Constants.PF_LICENSE_NAME, ""));
			mainOntologyMetadata.getLicense().setUrl(propertyFile.getProperty(Constants.PF_LICENSE_URI, ""));
			mainOntologyMetadata.getLicense().setIcon(propertyFile.getProperty(Constants.PF_LICENSE_ICON_URL, ""));
			mainOntologyMetadata.setStatus(propertyFile.getProperty(Constants.STATUS, "Specification Draft"));
			mainOntologyMetadata.setCiteAs(propertyFile.getProperty(Constants.PF_CITE_AS, ""));
			mainOntologyMetadata.setDoi(propertyFile.getProperty(Constants.PF_DOI, ""));
			mainOntologyMetadata.setDescription(propertyFile.getProperty(Constants.PF_DESCRIPTION, ""));
			mainOntologyMetadata.setLogo(propertyFile.getProperty(Constants.PF_LOGO, ""));
			mainOntologyMetadata.setBackwardsCompatibleWith(propertyFile.getProperty(Constants.COMPATIBLE, ""));
			mainOntologyMetadata.setIncompatibleWith(propertyFile.getProperty(Constants.PF_INCOMPATIBLE_WITH,""));
			this.setIntroText(propertyFile.getProperty(Constants.PF_INTRODUCTION, ""));
			// vocabLoadedSerialization =
			// propertyFile.getProperty(TextConstants.deafultSerialization, "RDF/XML");
			String serializationRDFXML = propertyFile.getProperty(Constants.PF_SERIALIZATION_RDF, "");
			if (!"".equals(serializationRDFXML)) {
				mainOntologyMetadata.addSerialization(Constants.RDF_XML, serializationRDFXML);
			}
			String serializationTTL = propertyFile.getProperty(Constants.PF_SERIALIZATION_TTL, "");
			if (!"".equals(serializationTTL)) {
				mainOntologyMetadata.addSerialization(Constants.TTL, serializationTTL);
			}
			String serializationNT = propertyFile.getProperty(Constants.PF_SERIALIZATION_NT,
					propertyFile.getProperty(Constants.PF_SERIALIZATION_N3, ""));
			if (!"".equals(serializationNT)) {
				mainOntologyMetadata.addSerialization(Constants.NT, serializationNT);
			}
			String serializationJSONLD = propertyFile.getProperty(Constants.PF_SERIALIZATION_JSON, "");
			if (!"".equals(serializationJSONLD)) {
				mainOntologyMetadata.addSerialization(Constants.JSON_LD, serializationJSONLD);
			}
			this.googleAnalyticsCode = propertyFile.getProperty("GoogleAnalyticsCode");
			String images = propertyFile.getProperty(Constants.PF_IMAGES, "");
			if (!"".equals(images)){
				mainOntologyMetadata.setImages(new ArrayList<>(Arrays.asList(images.split(";"))));
			}
			String source = propertyFile.getProperty(Constants.PF_SOURCE, "");
			if (!"".equals(source)){
				mainOntologyMetadata.setSources(new ArrayList<>(Arrays.asList(source.split(";"))));
			}
			String seeAlso = propertyFile.getProperty(Constants.PF_SEE_ALSO, "");
			if (!"".equals(seeAlso)){
				mainOntologyMetadata.setSeeAlso(new ArrayList<>(Arrays.asList(seeAlso.split(";"))));
			}
			String funders = propertyFile.getProperty(Constants.PF_FUNDERS, "");
			if (!"".equals(funders)){
				String [] fundersURI = funders.split(";");
				for (String s : fundersURI) {
					Agent a = new Agent();
					a.setName(s);
					a.setURL(s);
					mainOntologyMetadata.getFunders().add(a);
				}
			}
			String funding = propertyFile.getProperty(Constants.PF_FUNDING, "");
			if (!"".equals(funding)){
				mainOntologyMetadata.setFundingGrants(new ArrayList<String>(Arrays.asList(funding.split(";"))));
			}
			this.setAbstractPath(propertyFile.getProperty(Constants.PF_ABSTRACT_PATH, null));
			this.setDescriptionPath(propertyFile.getProperty(Constants.PF_DESCRIPTION_PATH, null));
			this.setIntroductionPath(propertyFile.getProperty(Constants.PF_INTRO_PATH, null));
			this.setOverviewPath(propertyFile.getProperty(Constants.PF_OVERVIEW_PATH, null));
			this.setReferencesPath(propertyFile.getProperty(Constants.PF_REFERENCES_PATH, null));
			loadPerLanguageSectionProperties();
			loadExtraResources();
			this.omitReadme = Boolean.parseBoolean(propertyFile.getProperty(Constants.PF_OMIT_README, "false"));
			this.kosHTML.clear();
			String kos = propertyFile.getProperty(Constants.PF_KOS_HTML, "");
			if (!kos.isEmpty()) {
				kosHTML.addAll(Arrays.asList(kos.split(";")));
			}
		String er = propertyFile.getProperty(Constants.PF_EXTRA_RESOURCES, "");
		if (!er.isEmpty()) {
			extraResources.addAll(Arrays.asList(er.split(";")));
		}
			mainOntologyMetadata.setCodeRepository(propertyFile.getProperty(Constants.PF_REFERENCES_CODE_REPO, ""));
		} catch (IOException ex) {
			// Only a warning, as we can continue safely without a property file.
			logger.warn("Error while reading configuration properties from [" + path + "]: " + ex.getMessage());
			throw ex;
		}
	}

	/**
	 * Method that given an ontology retrieves the main metadata properties into the
	 * configuration.
	 * 
	 * @param o Ontology to load properties from
	 */
	public void loadPropertiesFromOntology(OWLOntology o) {
		if (o == null) {
			return;
		}
		// EDINT extension: keep the serialization file names read from the conf,
		// because initializeOntology() resets them to the defaults. Ontology
		// annotations applied further down still take precedence.
		Map<String, String> confSerializations = new HashMap<>();
		for (String[] pair : new String[][] {
				{ Constants.RDF_XML, Constants.PF_SERIALIZATION_RDF },
				{ Constants.TTL, Constants.PF_SERIALIZATION_TTL },
				{ Constants.NT, Constants.PF_SERIALIZATION_NT },
				{ Constants.JSON_LD, Constants.PF_SERIALIZATION_JSON } }) {
			String v = propertyFile.getProperty(pair[1], "");
			if (isBlank(v) && Constants.NT.equals(pair[0])) {
				v = propertyFile.getProperty(Constants.PF_SERIALIZATION_N3, "");
			}
			if (!isBlank(v)) {
				confSerializations.put(pair[0], v);
			}
		}
		initializeOntology();
		for (Map.Entry<String, String> e : confSerializations.entrySet()) {
			mainOntologyMetadata.getSerializations().put(e.getKey(), e.getValue());
		}
		this.mainOntologyMetadata.setNamespacePrefix("[Ontology NS Prefix]");
		String uri;
		try {
			uri = o.getOntologyID().getOntologyIRI().get().toString();
		} catch (Exception e) {
			uri = "[Ontology URI]";
		}
		this.mainOntologyMetadata.setNamespaceURI(uri);
		String versionUri = null;
		try {
			versionUri = o.getOntologyID().getVersionIRI().get().toString();
		} catch (Exception e) {
			// versionUri = "[Version URI not provided]"; // if it is not present, do not
			// show it
		}
		// EDINT extension: process the declared owl:imports IRIs so that imports
		// that do not resolve are still listed (with the last IRI segment or a
		// local dcterms:title/rdfs:label as name), plus the rest of the loaded
		// imports closure when direct-imports-only is not set.
		Set<IRI> importIRIs = new LinkedHashSet<>();
		for (OWLImportsDeclaration decl : o.importsDeclarations().collect(java.util.stream.Collectors.toSet())) {
			importIRIs.add(decl.getIRI());
		}
		if (!isDisplayDirectImportsOnly()) {
			o.imports().forEach(i -> {
				i.getOntologyID().getOntologyIRI().ifPresent(importIRIs::add);
			});
		}
		Map<IRI, OWLOntology> loadedImports = new HashMap<>();
		o.imports().forEach(i -> {
			i.getOntologyID().getOntologyIRI().ifPresent(iri -> loadedImports.put(iri, i));
		});
		for (IRI iri : importIRIs) {
			initializeImportedOntology(iri, o, loadedImports.get(iri));
		}
		this.mainOntologyMetadata.setThisVersion(versionUri);
		// process ontology annotations
		o.annotations().forEach(a -> completeOntologyMetadata(a,o));

		// EDINT extension: labels for imports/extensions from widoco annotations
		applyVocabularyLabels();
		// EDINT extension: detect vocabularies reused without owl:imports
		detectReusedVocabularies(o);
		// EDINT extension: restore conf values for fields the ontology does not
		// annotate (initializeOntology() wiped them when -getOntologyMetadata is used)
		restoreConfValues(o);
		// in some cases, properties and data properties extend annotation properties, so we need to process them
		// separately. In this case we go through all axioms and look for any props that have the own ontology as subject
		for (OWLAxiom axiom : o.getAxioms()) {
			String subject = "", predicate ="", object ="";
			if (axiom instanceof OWLDataPropertyAssertionAxiom) {
				OWLDataPropertyAssertionAxiom dataPropertyAssertionAxiom = (OWLDataPropertyAssertionAxiom) axiom;
				subject = dataPropertyAssertionAxiom.getSubject().toStringID();
				predicate = dataPropertyAssertionAxiom.getProperty().asOWLDataProperty().toStringID();
				object = dataPropertyAssertionAxiom.getObject().getLiteral();
			} else if (axiom instanceof OWLObjectPropertyAssertionAxiom) {
				OWLObjectPropertyAssertionAxiom objectPropertyAssertionAxiom = (OWLObjectPropertyAssertionAxiom) axiom;
				subject = objectPropertyAssertionAxiom.getSubject().toStringID();
				predicate = objectPropertyAssertionAxiom.getProperty().asOWLObjectProperty().toStringID();
				object = objectPropertyAssertionAxiom.getObject().toStringID();
			}
			if (subject.equals(uri)){
				OWLDataFactory dataFactory = this.mainOntologyMetadata.getOWLAPIOntologyManager().getOWLDataFactory();
				OWLAnnotationProperty pAux = dataFactory.getOWLAnnotationProperty(IRI.create(predicate));
				OWLAnnotationValue oAux = dataFactory.getOWLLiteral(object);
				completeOntologyMetadata(dataFactory.getOWLAnnotation(pAux,oAux),o);
			}
		}

		if (isUseLicensius()) {
			String licName;
			String lic = GetLicense.getFirstLicenseFound(mainOntologyMetadata.getNamespaceURI());
			if (!lic.isEmpty() && !lic.equals("unknown")) {
				mainOntologyMetadata.getLicense().setUrl(lic);
				licName = GetLicense.getTitle(lic);
				mainOntologyMetadata.getLicense().setName(licName);
			}
		}
		if (this.mainOntologyMetadata.getName() == null || this.mainOntologyMetadata.getName().isEmpty()) {
			this.mainOntologyMetadata.setName(mainOntologyMetadata.getTitle());
		}
		if (mainOntologyMetadata.getStatus() == null || mainOntologyMetadata.getStatus().isEmpty()) {
			mainOntologyMetadata.setStatus("Ontology Specification Draft");
		}
		// default name if no annotations are found
		if (mainOntologyMetadata.getName() == null || mainOntologyMetadata.getName().isEmpty()) {
			this.mainOntologyMetadata.setName("[Ontology Name]");
		}
		// default citation if none is given
		if (mainOntologyMetadata.getCiteAs() == null || mainOntologyMetadata.getCiteAs().isEmpty()) {
			StringBuilder cite = new StringBuilder();
			for (Agent a : mainOntologyMetadata.getCreators()) {
				cite.append(a.getName()).append(", ");
			}
			if (cite.length() > 1) {
				// remove the last ","
				cite = new StringBuilder(cite.substring(0, cite.length() - 2));
				cite.append(".");
			}

			cite.append(appendDetails(mainOntologyMetadata.getTitle(), " ", true));
			cite.append(appendDetails(mainOntologyMetadata.getRevision(), " Revision: ", true));
			cite.append(appendDetails(mainOntologyMetadata.getThisVersion(), " Retrieved from: ", false));

			mainOntologyMetadata.setCiteAs(cite.toString());
		}
	}

	public void loadNamespaceDeclarations(OWLOntology o){
		//load all namespaces in the ontology document.
		this.namespaceDeclarations = new HashMap<>();
		OWLOntologyXMLNamespaceManager nsManager = new OWLOntologyXMLNamespaceManager(o, o.getFormat());
		for (String prefix : nsManager.getPrefixes()) {
			String namespaceURI = nsManager.getNamespaceForPrefix(prefix);
			if (prefix.isEmpty() || namespaceURI.equals(mainOntologyMetadata.getNamespaceURI())){
				namespaceDeclarations.put(mainOntologyMetadata.getNamespacePrefix(),namespaceURI);
			}else{
				namespaceDeclarations.put(prefix,nsManager.getNamespaceForPrefix(prefix));
			}
		}
	}

	/**
	 * EDINT extension: after reading ontology annotations, restore conf values for
	 * the fields the ontology does not annotate. The ontology always takes
	 * precedence; the conf only fills the gaps (e.g. latestVersionURI, publisher,
	 * status, citation, dates).
	 */
	/** Labels for well-known vocabularies, from the shipped resource. */
	private static Map<String, String> shippedVocabularyLabels;

	private static Map<String, String> shippedVocabularyLabels() {
		if (shippedVocabularyLabels == null) {
			shippedVocabularyLabels = new HashMap<>();
			// manual parsing: IRIs contain ':' which java.util.Properties would treat
			// as a key/value separator
			try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
					Configuration.class.getResourceAsStream(Constants.VOCABULARY_LABELS_RESOURCE), "UTF-8"))) {
				String line;
				while ((line = reader.readLine()) != null) {
					line = line.trim();
					if (line.isEmpty() || line.startsWith("#")) {
						continue;
					}
					int eq = line.indexOf('=');
					if (eq > 0) {
						shippedVocabularyLabels.put(normalizeNs(line.substring(0, eq)),
								line.substring(eq + 1).trim());
					}
				}
			} catch (Exception e) {
				System.err.println("Could not read vocabulary labels resource: " + e.getMessage());
			}
		}
		return shippedVocabularyLabels;
	}

	/**
	 * EDINT extension: labels provided through widoco:* annotations in the
	 * ontology (pairs of names/URIs separated by ';'), for imports, extensions and
	 * reused vocabularies.
	 */
	private Map<String, String> annotationLabels(String namesProp, String urisProp, String keySuffix) {
		Map<String, String> labels = new HashMap<>();
		String names = widocoAnnotationValues.get(namesProp);
		String uris = widocoAnnotationValues.get(urisProp);
		if (names == null || uris == null) {
			return labels;
		}
		String[] n = names.split(";");
		String[] u = uris.split(";");
		for (int i = 0; i < n.length && i < u.length; i++) {
			if (!n[i].trim().isEmpty() && !u[i].trim().isEmpty()) {
				labels.put(normalizeNs(u[i].trim()), n[i].trim());
			}
		}
		return labels;
	}

	/**
	 * EDINT extension: resolves the display label of a vocabulary: label provided
	 * in the ontology annotations, then shipped dictionary, then the last full
	 * segment of the IRI. Returns null when nothing better than the IRI is found.
	 */
	private String vocabularyLabel(String iri, Map<String, String> annotationLabels) {
		String ns = normalizeNs(iri);
		if (annotationLabels.containsKey(ns)) {
			return annotationLabels.get(ns);
		}
		if (shippedVocabularyLabels().containsKey(ns)) {
			return shippedVocabularyLabels().get(ns);
		}
		return null;
	}

	/** Last full segment of an IRI, prettified ("direccion-postal" -> "Direccion Postal"). */
	private static String lastSegmentLabel(String iri) {
		String base = iri.replaceAll("[#/]+$", "");
		String seg = base.substring(base.lastIndexOf('/') + 1);
		if (seg.isEmpty()) {
			return iri;
		}
		String pretty = seg.replace('-', ' ').replace('_', ' ').trim();
		if (pretty.length() > 1) {
			pretty = Character.toUpperCase(pretty.charAt(0)) + pretty.substring(1);
		}
		return pretty;
	}

	/** Relabels imports and extensions from annotations and disambiguates duplicates. */
	private void applyVocabularyLabels() {
		Map<String, String> impLabels = annotationLabels(Constants.PROP_WIDOCO_IMPORTED_NAMES,
				Constants.PROP_WIDOCO_IMPORTED_URIS, "imported");
		Map<String, String> extLabels = annotationLabels(Constants.PROP_WIDOCO_EXTENDED_NAMES,
				Constants.PROP_WIDOCO_EXTENDED_URIS, "extended");
		relabel(mainOntologyMetadata.getImportedOntologies(), impLabels);
		relabel(mainOntologyMetadata.getExtendedOntologies(), extLabels);
	}

	private void relabel(List<Ontology> ontos, Map<String, String> annotationLabels) {
		for (Ontology o : ontos) {
			String iri = o.getNamespaceURI();
			if (iri == null || iri.isEmpty()) {
				continue;
			}
			String label = vocabularyLabel(iri, annotationLabels);
			if (label == null) {
				label = lastSegmentLabel(iri);
			}
			o.setName(label);
		}
		disambiguate(ontos);
	}

	/** Adds a hint (last two segments of the host/path) when labels collide. */
	private static void disambiguate(List<Ontology> ontos) {
		Map<String, Integer> counts = new HashMap<>();
		for (Ontology o : ontos) {
			counts.merge(o.getName(), 1, Integer::sum);
		}
		for (Ontology o : ontos) {
			if (counts.getOrDefault(o.getName(), 0) > 1) {
				String iri = o.getNamespaceURI() == null ? "" : o.getNamespaceURI();
				String host = iri.replaceFirst("^[a-zA-Z]+://", "").replaceFirst("[/#].*$", "");
				o.setName(o.getName() + " (" + host + ")");
			}
		}
	}

	/**
	 * EDINT extension: detect vocabularies reused without owl:imports. Collects
	 * the namespaces of external entities referenced from subClassOf,
	 * subPropertyOf, domain/range and individual class assertions, excluding the
	 * ontology namespace, common metadata namespaces and namespaces already
	 * listed as imports or extensions. Labels come from the conf keys
	 * reusedVocabularyNames/reusedVocabularyURIs when provided.
	 */
	private void detectReusedVocabularies(OWLOntology o) {
		reusedVocabularies.clear();
		String ownNs = normalizeNs(mainOntologyMetadata.getNamespaceURI());
		Set<String> excluded = new HashSet<>();
		excluded.add(ownNs);
		for (String ns : Arrays.asList(
				"http://www.w3.org/1999/02/22-rdf-syntax-ns#",
				"http://www.w3.org/2000/01/rdf-schema#",
				"http://www.w3.org/2002/07/owl#",
				"http://www.w3.org/XML/1998/namespace",
				"http://www.w3.org/2001/XMLSchema#",
				"http://purl.org/dc/terms/",
				"http://purl.org/dc/elements/1.1",
				"http://xmlns.com/foaf/0.1",
				"https://schema.org", "http://schema.org",
				"http://purl.org/vocab/vann",
				"http://purl.org/NET/bibo",
				"http://purl.org/pav",
				"http://www.w3.org/ns/prov",
				"http://www.w3.org/2004/02/skos/core",
				"http://www.w3.org/ns/dx/prof",
				"http://xmlns.com/widoco",
				"https://w3id.org/widoco/vocab")) {
			excluded.add(normalizeNs(ns));
		}
		for (Ontology i : mainOntologyMetadata.getImportedOntologies()) {
			excluded.add(normalizeNs(i.getNamespaceURI()));
		}
		for (Ontology e : mainOntologyMetadata.getExtendedOntologies()) {
			excluded.add(normalizeNs(e.getNamespaceURI()));
		}
		Map<String, Integer> nsCount = new HashMap<>();
		for (OWLAxiom ax : o.axioms().collect(java.util.stream.Collectors.toList())) {
			AxiomType<?> t = ax.getAxiomType();
			boolean relevant = t == AxiomType.SUBCLASS_OF || t == AxiomType.EQUIVALENT_CLASSES
					|| t == AxiomType.SUB_OBJECT_PROPERTY || t == AxiomType.EQUIVALENT_OBJECT_PROPERTIES
					|| t == AxiomType.EQUIVALENT_DATA_PROPERTIES
					|| t == AxiomType.SUB_DATA_PROPERTY
					|| t == AxiomType.OBJECT_PROPERTY_DOMAIN || t == AxiomType.OBJECT_PROPERTY_RANGE
					|| t == AxiomType.DATA_PROPERTY_DOMAIN || t == AxiomType.DATA_PROPERTY_RANGE
					|| t == AxiomType.CLASS_ASSERTION;
			if (!relevant) {
				continue;
			}
			for (OWLEntity e : ax.getSignature()) {
				if (e.isOWLClass() || e.isOWLObjectProperty() || e.isOWLDataProperty()) {
					String ns = normalizeNs(e.getIRI().getIRIString().startsWith("urn:")
							? e.getIRI().toString() : namespaceOf(e.getIRI().toString()));
					if (!excluded.contains(ns)) {
						nsCount.merge(ns, 1, Integer::sum);
					}
				}
			}
		}
		// labels from conf (reusedVocabularyNames/URIs)
		Map<String, String> confLabels = new HashMap<>();
		String[] names = propertyFile.getProperty(Constants.PF_REUSED_VOCABULARY_NAMES, "").split(";");
		String[] uris = propertyFile.getProperty(Constants.PF_REUSED_VOCABULARY_URIS, "").split(";");
		for (int i = 0; i < names.length && i < uris.length; i++) {
			if (!isBlank(names[i])) {
				confLabels.put(normalizeNs(uris[i]), names[i]);
			}
		}
		for (Map.Entry<String, Integer> e : nsCount.entrySet()) {
			Ontology ont = new Ontology();
			ont.setNamespaceURI(e.getKey());
			String label = confLabels.getOrDefault(e.getKey(), null);
			if (isBlank(label)) {
				label = vocabularyLabel(e.getKey(), annotationLabels(Constants.PROP_WIDOCO_REUSED_NAMES,
						Constants.PROP_WIDOCO_REUSED_URIS, "reused"));
			}
			if (isBlank(label)) {
				label = lastSegmentLabel(e.getKey());
			}
			ont.setName(label);
			reusedVocabularies.add(ont);
		}
		disambiguate(reusedVocabularies);
		reusedVocabularies.sort(Comparator.comparing(Ontology::getName));
	}

	private static String namespaceOf(String iri) {
		int hash = iri.lastIndexOf('#');
		int slash = iri.lastIndexOf('/');
		int cut = Math.max(hash, slash);
		return cut > 0 ? iri.substring(0, cut + 1) : iri;
	}

	private static String normalizeNs(String ns) {
		if (ns == null) {
			return "";
		}
		return ns.replaceAll("[#/]+$", "");
	}

	private void restoreConfValues(OWLOntology o) {
		if (isBlank(mainOntologyMetadata.getLatestVersion())) {
			mainOntologyMetadata.setLatestVersion(propertyFile.getProperty(Constants.PF_LATEST_VERSION_URI, ""));
		}
		if (isBlank(mainOntologyMetadata.getPreviousVersion())) {
			mainOntologyMetadata.setPreviousVersion(propertyFile.getProperty(Constants.PF_PREVIOUS_VERSION, ""));
		}
		if (isBlank(mainOntologyMetadata.getCiteAs())) {
			mainOntologyMetadata.setCiteAs(propertyFile.getProperty(Constants.PF_CITE_AS, ""));
		}
		if (isBlank(mainOntologyMetadata.getStatus())) {
			mainOntologyMetadata.setStatus(propertyFile.getProperty(Constants.STATUS, ""));
		}
		if (isBlank(mainOntologyMetadata.getCreationDate())) {
			mainOntologyMetadata.setCreationDate(propertyFile.getProperty(Constants.PF_DATE_CREATED, ""));
		}
		if (isBlank(mainOntologyMetadata.getIssuedDate())) {
			mainOntologyMetadata.setIssuedDate(propertyFile.getProperty(Constants.PF_DATE_ISSUED, ""));
		}
		if (isBlank(mainOntologyMetadata.getModifiedDate())) {
			mainOntologyMetadata.setModifiedDate(propertyFile.getProperty(Constants.PF_DATE_MODIFIED, ""));
		}
		if (isBlank(mainOntologyMetadata.getTitle())) {
			mainOntologyMetadata.setTitle(propertyFile.getProperty(Constants.PF_ONT_TITLE, ""));
		}
		// publisher agent from conf (name, URL, institution)
		if (mainOntologyMetadata.getPublisher() == null || isBlank(mainOntologyMetadata.getPublisher().getName())) {
			Agent ag = new Agent();
			ag.setName(propertyFile.getProperty(Constants.PF_PUBLISHER, ""));
			ag.setURL(propertyFile.getProperty(Constants.PF_PUBLISHER_URI, ""));
			ag.setInstitutionName(propertyFile.getProperty(Constants.PF_PUBLISHER_INSTITUTION, ""));
			ag.setInstitutionURL(propertyFile.getProperty(Constants.PF_PUBLISHER_INSTITUTION_URI, ""));
			mainOntologyMetadata.setPublisher(ag);
		}
		// creators from conf authors list if the ontology declares none
		if (mainOntologyMetadata.getCreators().isEmpty() && !isBlank(propertyFile.getProperty(Constants.PF_AUTHORS, ""))) {
			for (String a : propertyFile.getProperty(Constants.PF_AUTHORS, "").split(";")) {
				if (!a.trim().isEmpty()) {
					Agent ag = new Agent();
					ag.setName(a.trim());
					mainOntologyMetadata.getCreators().add(ag);
				}
			}
		}
		// namespaces: initializeOntology() cleared the declarations loaded before
		// this call, so re-derive them from the ontology
		loadNamespaceDeclarations(o);
	}

	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	private String appendDetails(final String detail, final String prefix, final boolean useFullStop) {
		if (detail == null || detail.isEmpty()) {
			return "";
		}

		return (prefix + detail + (useFullStop ? "." : ""));
	}

	private void completeOntologyMetadata(OWLAnnotation a, OWLOntology o) {
		String propertyName = a.getProperty().getIRI().getIRIString();
		String value;
		String valueLanguage;
		//System.out.println(propertyName);
		switch (propertyName) {
		case Constants.PROP_RDFS_LABEL:
		case Constants.PROP_SKOS_PREF_LABEL:
		case Constants.PROP_SCHEMA_ALTERNATE_NAME_HTTP:
		case Constants.PROP_SCHEMA_ALTERNATE_NAME_HTTPS:
		case Constants.PROP_MOD_ACRONYM:
			try {
				valueLanguage = a.getValue().asLiteral().get().getLang();
				value = a.getValue().asLiteral().get().getLiteral();
				if (this.currentLanguage.equals(valueLanguage)
						|| (mainOntologyMetadata.getName() == null || "".equals(mainOntologyMetadata.getName()))) {
					this.mainOntologyMetadata.setName(value);
				}
			} catch (Exception e) {
				logger.error("Error while getting ontology label. No literal provided");
			}
			break;
		case Constants.PROP_DC_TITLE:
		case Constants.PROP_DCTERMS_TITLE:
		case Constants.PROP_SCHEMA_NAME_HTTP:
		case Constants.PROP_SCHEMA_NAME_HTTPS:
			try {
				valueLanguage = a.getValue().asLiteral().get().getLang();
				value = a.getValue().asLiteral().get().getLiteral();
				if (this.currentLanguage.equals(valueLanguage)
						|| (mainOntologyMetadata.getTitle() == null || "".equals(mainOntologyMetadata.getTitle()))) {
					this.mainOntologyMetadata.setTitle(value);
				}
			} catch (Exception e) {
				logger.error("Error while getting ontology title. No literal provided");
			}
			break;
		case Constants.PROP_DCTERMS_ABSTRACT:
		case Constants.PROP_DC_ABSTRACT:
			try {
				valueLanguage = a.getValue().asLiteral().get().getLang();
				value = a.getValue().asLiteral().get().getLiteral();
				if (this.currentLanguage.equals(valueLanguage)
						|| (abstractSection == null || abstractSection.isEmpty())) {
					abstractSection = value;
					this.setIncludeAbstract(true); // in case users set no place holder text but added their own
				}
			} catch (Exception e) {
				logger.error("Error while getting ontology abstract. No literal provided");
			}
			break;
		case Constants.PROP_DCTERMS_DESCRIPTION:
		case Constants.PROP_DC_DESCRIPTION:
		case Constants.PROP_SCHEMA_DESCRIPTION_HTTP:
		case Constants.PROP_SCHEMA_DESCRIPTION_HTTPS:
		case Constants.PROP_RDFS_COMMENT:
		case Constants.PROP_SKOS_NOTE:
			try {
				valueLanguage = a.getValue().asLiteral().get().getLang();
				value = a.getValue().asLiteral().get().getLiteral();
				if (this.currentLanguage.equals(valueLanguage)
						|| (mainOntologyMetadata.getDescription() == null
						||	mainOntologyMetadata.getDescription().isEmpty())) {
					mainOntologyMetadata.setDescription(value);
					this.setIncludeDescription(true);
				}
			} catch (Exception e) {
				logger.error("Error while getting ontology description. No literal provided");
			}
			break;
		case Constants.PROP_DCTERMS_REPLACES:
		case Constants.PROP_DC_REPLACES:
		case Constants.PROP_PROV_WAS_REVISION_OF:
		case Constants.PROP_OWL_PRIOR_VERSION:
		case Constants.PROP_PAV_PREVIOUS_VERSION:
			value = WidocoUtils.getValueAsLiteralOrURI(a.getValue());
			mainOntologyMetadata.setPreviousVersion(value);
			break;
		case Constants.PROP_OWL_VERSION_INFO:
		case Constants.PROP_SCHEMA_SCHEMA_VERSION_HTTP:
		case Constants.PROP_SCHEMA_SCHEMA_VERSION_HTTPS:
		case Constants.PROP_PAV_VERSION:
		case Constants.PROP_DCTERMS_HAS_VERSION:
			try {
				value = a.getValue().asLiteral().get().getLiteral();
				mainOntologyMetadata.setRevision(value);
			} catch (Exception e) {
				logger.error("Error while getting ontology abstract. No literal provided");
			}
			break;
		case Constants.PROP_VANN_PREFIX:
			value = WidocoUtils.getValueAsLiteralOrURI(a.getValue());
			mainOntologyMetadata.setNamespacePrefix(value);
			break;
		case Constants.PROP_VANN_URI:
			value = WidocoUtils.getValueAsLiteralOrURI(a.getValue());
			mainOntologyMetadata.setNamespaceURI(value);
			break;
		case Constants.PROP_DCTERMS_LICENSE:
		case Constants.PROP_DC_RIGHTS:
		case Constants.PROP_SCHEMA_LICENSE_HTTP:
		case Constants.PROP_SCHEMA_LICENSE_HTTPS:
		case Constants.PROP_CC_LICENSE:
			try {
				value = WidocoUtils.getValueAsLiteralOrURI(a.getValue());
				License l = new License();
				if (isURL(value)) {
					l.setUrl(value);
					l.setName(value);
				} else {
					l.setName(value);
				}
				mainOntologyMetadata.setLicense(l);
			} catch (Exception e) {
				logger.error("Could not retrieve license. Please avoid using blank nodes...");
			}
			break;
		case Constants.PROP_DC_CONTRIBUTOR:
		case Constants.PROP_DCTERMS_CONTRIBUTOR:
		case Constants.PROP_SCHEMA_CONTRIBUTOR_HTTP:
		case Constants.PROP_SCHEMA_CONTRIBUTOR_HTTPS:
		case Constants.PROP_PAV_CONTRIBUTED_BY:
		case Constants.PROP_DC_CREATOR:
		case Constants.PROP_DCTERMS_CREATOR:
		case Constants.PROP_SCHEMA_CREATOR_HTTP:
		case Constants.PROP_SCHEMA_CREATOR_HTTPS:
		case Constants.PROP_PAV_CREATED_BY:
		case Constants.PROP_PROV_ATTRIBUTED_TO:
		case Constants.PROP_DC_PUBLISHER:
		case Constants.PROP_DCTERMS_PUBLISHER:
		case Constants.PROP_SCHEMA_PUBLISHER_HTTP:
		case Constants.PROP_SCHEMA_PUBLISHER_HTTPS:
		case Constants.PROP_SCHEMA_FUNDER_HTTP:
		case Constants.PROP_SCHEMA_FUNDER_HTTPS:
		case Constants.PROP_FOAF_FUNDED_BY:
			try {
				Agent ag = new Agent();
				if (a.getValue().isLiteral()) {
					ag.setURL("");
					ag.setName(a.getValue().asLiteral().get().getLiteral());
				}else{
					if (!a.getValue().asAnonymousIndividual().isEmpty()){
						// dealing with a blank node, extract metadata from URL, name and organization (if available)
						o.getAnnotationAssertionAxioms(a.getValue().asAnonymousIndividual().get()).stream().forEach(i -> {
							completeAgentMetadata(i, ag, o);
						});
					}else{
						IRI valueURI = a.getValue().asIRI().get();
						o.getAnnotationAssertionAxioms(valueURI).stream().forEach(i -> {
							completeAgentMetadata(i, ag, o);
						});
						if(ag.getName()==null || ag.getName().isEmpty()){
							//the value does not have annotations, so we keep it as it is.
							ag.setName(valueURI.getIRIString());
						}
						if(ag.getURL()==null || ag.getURL().isEmpty()){
							ag.setURL(valueURI.getIRIString());
						}
					}
				}
				switch (propertyName) {
				case Constants.PROP_DC_CONTRIBUTOR:
				case Constants.PROP_DCTERMS_CONTRIBUTOR:
				case Constants.PROP_SCHEMA_CONTRIBUTOR_HTTP:
				case Constants.PROP_SCHEMA_CONTRIBUTOR_HTTPS:
				case Constants.PROP_PAV_CONTRIBUTED_BY:
					mainOntologyMetadata.getContributors().add(ag);
					break;
				case Constants.PROP_DC_CREATOR:
				case Constants.PROP_DCTERMS_CREATOR:
				case Constants.PROP_PAV_CREATED_BY:
				case Constants.PROP_PROV_ATTRIBUTED_TO:
				case Constants.PROP_SCHEMA_CREATOR_HTTP:
				case Constants.PROP_SCHEMA_CREATOR_HTTPS:
					mainOntologyMetadata.getCreators().add(ag);
					break;
				case Constants.PROP_SCHEMA_FUNDER_HTTP:
				case Constants.PROP_SCHEMA_FUNDER_HTTPS:
				case Constants.PROP_FOAF_FUNDED_BY:
					if(ag.getURL() == null || ag.getURL().isEmpty())
						ag.setURL(ag.getName());
					mainOntologyMetadata.getFunders().add(ag);
					break;
				default:
					mainOntologyMetadata.setPublisher(ag);
					break;
				}
			} catch (Exception e) {
				logger.error("Could not retrieve creator/contributor.");
			}
			break;
		case Constants.PROP_DCTERMS_CREATED:
		case Constants.PROP_SCHEMA_DATE_CREATED_HTTP:
		case Constants.PROP_SCHEMA_DATE_CREATED_HTTPS:
		case Constants.PROP_PROV_GENERATED_AT_TIME:
		case Constants.PROP_PAV_CREATED_ON:
			value = a.getValue().asLiteral().get().getLiteral();
			mainOntologyMetadata.setCreationDate(value);
			break;
		case Constants.PROP_DCTERMS_MODIFIED:
		case Constants.PROP_SCHEMA_DATE_MODIFIED_HTTP:
		case Constants.PROP_SCHEMA_DATE_MODIFIED_HTTPS:
		case Constants.PROP_PAV_LAST_UPDATED_ON:
			value = a.getValue().asLiteral().get().getLiteral();
			mainOntologyMetadata.setModifiedDate(value);
			break;
		case Constants.PROP_SCHEMA_DATE_ISSUED_HTTP:
		case Constants.PROP_SCHEMA_DATE_ISSUED_HTTPS:
		case Constants.PROP_DCTERMS_ISSUED:
			value = a.getValue().asLiteral().get().getLiteral();
			mainOntologyMetadata.setIssuedDate(value);
			break;
		case Constants.PROP_DCTERMS_BIBLIOGRAPHIC_CIT:
		case Constants.PROP_SCHEMA_CITATION_HTTP:
		case Constants.PROP_SCHEMA_CITATION_HTTPS:
			value = WidocoUtils.getValueAsLiteralOrURI(a.getValue());
			mainOntologyMetadata.setCiteAs(value);
			break;
		case Constants.PROP_BIBO_DOI:
			value = WidocoUtils.getValueAsLiteralOrURI(a.getValue());
			mainOntologyMetadata.setDoi(value);
			break;
		case Constants.PROP_BIBO_STATUS:
		case Constants.PROP_MOD_STATUS:
		case Constants.PROP_SCHEMA_STATUS_HTTP:
		case Constants.PROP_SCHEMA_STATUS_HTTPS:
			value = "Specification Draft";
			try {
				//if an object property is used, all valid status have the form http://purl.org/ontology/bibo/status/
				value = a.getValue().asIRI().get().getIRIString().replace(Constants.NS_BIBO+"status/","");
			}catch(Exception e){
				try{
					value = a.getValue().asLiteral().get().getLiteral();
				} catch (Exception e1) {
					logger.error("Error while getting the status. No valid literal or URI provided");
				}
			}
			mainOntologyMetadata.setStatus(value);
			break;
		case Constants.PROP_OWL_BACKWARDS_COMPATIBLE:
			value = WidocoUtils.getValueAsLiteralOrURI(a.getValue());
			mainOntologyMetadata.setBackwardsCompatibleWith(value);
			break;
		case Constants.PROP_OWL_INCOMPATIBLE:
			value = WidocoUtils.getValueAsLiteralOrURI(a.getValue());
			mainOntologyMetadata.setIncompatibleWith(value);
			break;
		case Constants.PROP_SCHEMA_IMAGE_HTTP:
		case Constants.PROP_SCHEMA_IMAGE_HTTPS:
		case Constants.PROP_FOAF_IMAGE:
		case Constants.PROP_FOAF_DEPICTION:
			value = WidocoUtils.getValueAsLiteralOrURI(a.getValue());
			mainOntologyMetadata.addImage(value);
			break;
		case Constants.PROP_SCHEMA_LOGO_HTTP:
		case Constants.PROP_SCHEMA_LOGO_HTTPS:
		case Constants.PROP_FOAF_LOGO:
			value = WidocoUtils.getValueAsLiteralOrURI(a.getValue());
			mainOntologyMetadata.setLogo(value);
			break;
		case Constants.PROP_WIDOCO_IMPORTED_NAMES:
		case Constants.PROP_WIDOCO_IMPORTED_URIS:
		case Constants.PROP_WIDOCO_EXTENDED_NAMES:
		case Constants.PROP_WIDOCO_EXTENDED_URIS:
		case Constants.PROP_WIDOCO_REUSED_NAMES:
		case Constants.PROP_WIDOCO_REUSED_URIS:
			value = WidocoUtils.getValueAsLiteralOrURI(a.getValue());
			widocoAnnotationValues.put(propertyName, value);
			break;
		case Constants.PROP_VOAF_EXTENDS:
			value = WidocoUtils.getValueAsLiteralOrURI(a.getValue());
			Ontology ont = new Ontology();
			ont.setNamespaceURI(value);
			ont.setName(value);
			mainOntologyMetadata.getExtendedOntologies().add(ont);
			break;
		case Constants.PROP_WDRS_IS_DESCRIBED_BY:
		case Constants.PROP_DC_SOURCE:
		case Constants.PROP_DCTERMS_SOURCE:
		case Constants.PROP_PROV_HAD_PRIMARY_SOURCE:
			value = WidocoUtils.getValueAsLiteralOrURI(a.getValue());
			mainOntologyMetadata.getSources().add(value);
			break;
		case Constants.PROP_SCHEMA_FUNDING_HTTP:
		case Constants.PROP_SCHEMA_FUNDING_HTTPS:
			value = WidocoUtils.getValueAsLiteralOrURI(a.getValue());
			mainOntologyMetadata.getFundingGrants().add(value);
			break;
		case Constants.PROP_RDFS_SEE_ALSO:
			value = WidocoUtils.getValueAsLiteralOrURI(a.getValue());
			mainOntologyMetadata.getSeeAlso().add(value);
			break;
		case Constants.PROP_WIDOCO_INTRODUCTION:
			try {
				valueLanguage = a.getValue().asLiteral().get().getLang();
				value = a.getValue().asLiteral().get().getLiteral();
				if (this.currentLanguage.equals(valueLanguage)
						|| (introText == null || introText.isEmpty())) {
					introText = value;
					this.setIncludeIntroduction(true);
				}
			} catch (Exception e) {
				logger.error("Error while introduction annotation. No literal provided");
			}
			break;
		//serializations (rare as it is usually available with C/N)
		case Constants.PROP_WIDOCO_JSON_LD:
			value = WidocoUtils.getValueAsLiteralOrURI(a.getValue());
			mainOntologyMetadata.getSerializations().replace(Constants.JSON_LD, value);
			break;
		case Constants.PROP_WIDOCO_NT:
			value = WidocoUtils.getValueAsLiteralOrURI(a.getValue());
			mainOntologyMetadata.getSerializations().replace(Constants.NT, value);
			break;
		case Constants.PROP_WIDOCO_RDF_XML:
			value = WidocoUtils.getValueAsLiteralOrURI(a.getValue());
			mainOntologyMetadata.getSerializations().replace(Constants.RDF_XML, value);
			break;
		case Constants.PROP_WIDOCO_TURTLE:
			value = WidocoUtils.getValueAsLiteralOrURI(a.getValue());
			mainOntologyMetadata.getSerializations().replace(Constants.TTL, value);
			break;
		case Constants.PROP_DOAP_REPO:
		case Constants.PROP_SCHEMA_CODE_REPO_HTTP:
		case Constants.PROP_SCHEMA_CODE_REPO_HTTPS:
			value = WidocoUtils.getValueAsLiteralOrURI(a.getValue());
			mainOntologyMetadata.setCodeRepository(value);
			break;
		}
	}

	/**
	 * Method that given an annotation, an agent and an ontology, it will try to retrieve the name, URL
	 * and institution name/URL that agent belongs to.
	 * @param ann annotation axiom about an agent (can be a bank node or a URI)
	 * @param ag agent to complete metadata from
	 * @param o ontology model (needed in case further search is required)
	 */
	private void completeAgentMetadata(OWLAnnotationAssertionAxiom ann, Agent ag, OWLOntology o) {
		String propertyName = ann.getProperty().getIRI().getIRIString();
		String nameFragment;
//		System.out.println(propertyName);
		switch (propertyName) {
			case Constants.PROP_RDFS_LABEL:
			case Constants.PROP_SCHEMA_NAME_HTTP:
			case Constants.PROP_SCHEMA_NAME_HTTPS:
			case Constants.PROP_VCARD_FN:
			case Constants.PROP_FOAF_NAME:
			case Constants.PROP_VCARD_FN_OLD:
				ag.setName(WidocoUtils.getValueAsLiteralOrURI(ann.getValue()));
				break;
			case Constants.PROP_SCHEMA_GIVEN_NAME_HTTP:
			case Constants.PROP_SCHEMA_GIVEN_NAME_HTTPS:
			case Constants.PROP_VCARD_GIVEN_NAME:
			case Constants.PROP_VCARD_GIVEN_OLD:
			case Constants.PROP_FOAF_GIVEN_NAME:
				nameFragment = WidocoUtils.getValueAsLiteralOrURI(ann.getValue());
				if (ag.getName() == null){
					ag.setName(nameFragment);
				}else{
					if(!ag.getName().contains(nameFragment)){
					ag.setName(nameFragment + " " +ag.getName());
					}
				}
				break;
			case Constants.PROP_SCHEMA_FAMILY_NAME_HTTP:
			case Constants.PROP_SCHEMA_FAMILY_NAME_HTTPS:
			case Constants.PROP_VCARD_FAMILY_NAME:
			case Constants.PROP_VCARD_FAMILY_OLD:
			case Constants.PROP_FOAF_FAMILY_NAME:
				nameFragment = WidocoUtils.getValueAsLiteralOrURI(ann.getValue());
				if (ag.getName() == null){
					ag.setName(nameFragment);
				}else {
					if(!ag.getName().contains(nameFragment)){
						ag.setName(ag.getName() + " " + nameFragment);
					}
				}
				break;
			case Constants.PROP_SCHEMA_URL_HTTP:
			case Constants.PROP_SCHEMA_URL_HTTPS:
			case Constants.PROP_FOAF_HOME_PAGE:
			case Constants.PROP_VCARD_HAS_URL:
			case Constants.PROP_VCARD_URL:
				ag.setURL(WidocoUtils.getValueAsLiteralOrURI(ann.getValue()));
				break;
			case Constants.PROP_SCHEMA_EMAIL_HTTP:
			case Constants.PROP_SCHEMA_EMAIL_HTTPS:
			case Constants.PROP_FOAF_MBOX:
			case Constants.PROP_VCARD_EMAIL:
			case Constants.PROP_VCARD_EMAIL_OLD:
				ag.setEmail(WidocoUtils.getValueAsLiteralOrURI(ann.getValue()));
				break;
			case Constants.PROP_SCHEMA_AFFILIATION_HTTP:
			case Constants.PROP_SCHEMA_AFFILIATION_HTTPS:
			case Constants.PROP_ORG_MEMBER_OF:
				if (ann.getValue().isLiteral()) {
					String literalValue = ann.getValue().asLiteral().get().getLiteral();
					if (literalValue.contains("http")){
						ag.setInstitutionURL(literalValue);
						ag.setInstitutionName(literalValue);
					}
				}else{
					Agent aux = new Agent(); // store the information about the organization as an aux agent
					if (!ann.getValue().asAnonymousIndividual().isEmpty()){
						// dealing with a blank node, extract metadata from URL, name and organization (if available)
						o.getAnnotationAssertionAxioms(ann.getValue().asAnonymousIndividual().get()).stream().forEach(i -> {
							completeAgentMetadata(i,aux,o);
						});
					}else{
						IRI valueURI = ann.getValue().asIRI().get();
						o.getAnnotationAssertionAxioms(valueURI).stream().forEach(i -> {
							completeAgentMetadata(i,aux,o);
						});
						if(aux.getName()==null || aux.getName().isEmpty()){
							//the value does not have annotations, so we keep it as it is.
							aux.setName(valueURI.getIRIString());
						}
						if(aux.getURL()==null || aux.getURL().isEmpty()){
							aux.setURL(valueURI.getIRIString());
						}
					}
					//copy aux data into ag
					if (aux.getName()!=null){
						ag.setInstitutionName(aux.getName());
					}
					if (aux.getURL()!=null){
						ag.setInstitutionURL(aux.getURL());
					}
				}
				break;
			}

	}

	private boolean isURL(String s) {
		try {
			URL url = new URL(s);
			url.toURI();
			return true;
		} catch (MalformedURLException | URISyntaxException e) {
			return false;
		}
	}

	public String getGoogleAnalyticsCode() {
		return googleAnalyticsCode;
	}

	public void setGoogleAnalyticsCode(String googleAnalyticsCode) {
		this.googleAnalyticsCode = googleAnalyticsCode;
	}

	public void setOverwriteAll(boolean s) {
		this.overwriteAll = s;
	}

	public boolean getOverWriteAll() {
		return overwriteAll;
	}

	public boolean isFromFile() {
		return fromFile;
	}

	public void reloadPropertyFile(String path) {
		// NOTE: here we can avoid propagating the exception again
		try {
			this.loadPropertyFile(path);
		} catch (IOException ex) {
			// Only a warning, as we can continue safely without a property file.
			logger.warn("Error while reading configuration properties from [" + path + "]: " + ex.getMessage());
		}
	}

	/**
	 * returns the path where the documentation will be generated.
	 * 
	 * @return documentation path
	 */
	public String getDocumentationURI() {
		return documentationURI;
	}

	public Ontology getMainOntology() {
		return mainOntologyMetadata;
	}

	public String getInputOntology() {
		return ((this.isFromFile() ? "File: " : "URI: ") + getOntologyPath());
	}

	public String getOntologyPath() {
		return ontologyPath;
	}

	public String getOntologyURI() {
		return this.mainOntologyMetadata.getNamespaceURI();
	}

	public boolean isPublishProvenance() {
		return publishProvenance;
	}

	public boolean isChangeLogSuccessfullyCreated() {
		return changeLogSuccessfullyCreated;
	}

	public void setChangeLogSuccessfullyCreated(boolean changeLogSuccessfullyCreated) {
		this.changeLogSuccessfullyCreated = changeLogSuccessfullyCreated;
	}

	public void setDocumentationURI(String documentationURI) {
		this.documentationURI = documentationURI;
	}

	public void setMainOntology(Ontology mainOntology) {
		this.mainOntologyMetadata = mainOntology;
	}

	public void setOntologyPath(String ontologyPath) {
		this.ontologyPath = ontologyPath;
	}

	public void setOntologyURI(String ontologyURI) {
		this.mainOntologyMetadata.setNamespaceURI(ontologyURI);
	}

	public void setImports(List<String> imports) {
		for (String importStr:imports) {
			this.imports.add(new File(importStr));
		}
	}

	public void setPublishProvenance(boolean publishProvenance) {
		this.publishProvenance = publishProvenance;
	}

	public String getAbstractPath() {
		return abstractPath;
	}

	/**
	 * EDINT extension: resolves a per-language property with fallback to the
	 * language-agnostic value.
	 */
	private String resolveByLang(Map<String, String> byLang, String lang, String fallback) {
		if (lang != null && byLang.containsKey(lang)) {
			return byLang.get(lang);
		}
		return fallback;
	}

	public String getAbstractSection(String lang) {
		return resolveByLang(abstractSectionByLang, lang, abstractSection);
	}

	public String getDescription(String lang) {
		return resolveByLang(descriptionSectionByLang, lang, mainOntologyMetadata.getDescription());
	}

	public String getReferences(String lang) {
		return resolveByLang(referencesSectionByLang, lang, "");
	}

	public String getIntroText(String lang) {
		return resolveByLang(introductionSectionByLang, lang, introText);
	}

	public String getAbstractPath(String lang) {
		return resolveByLang(abstractPathByLang, lang, abstractPath);
	}

	public String getDescriptionPath(String lang) {
		return resolveByLang(descriptionPathByLang, lang, descriptionPath);
	}

	public String getReferencesPath(String lang) {
		return resolveByLang(referencesPathByLang, lang, referencesPath);
	}

	public List<String> getExtraCSS() {
		return extraCSS;
	}

	public Map<String, String> getPerLanguageAbstractSections() {
		return abstractSectionByLang;
	}

	public Map<String, String> getPerLanguageDescriptions() {
		return descriptionSectionByLang;
	}

	public boolean hasPerLanguageDescription(String lang) {
		return lang != null && descriptionSectionByLang.containsKey(lang);
	}

	public Map<String, String> getPerLanguageReferences() {
		return referencesSectionByLang;
	}

	public List<String> getExtraJS() {
		return extraJS;
	}

	public List<String> getExtraResources() {
		return extraResources;
	}

	public boolean isOmitReadme() {
		return omitReadme;
	}

	public List<String> getKosHTML() {
		return kosHTML;
	}

	public List<Ontology> getReusedVocabularies() {
		return reusedVocabularies;
	}

	public void setOmitReadme(boolean omitReadme) {
		this.omitReadme = omitReadme;
	}

	/**
	 * EDINT extension: loads per-language section content and paths from keys
	 * with a language suffix, e.g. "abstract-en", "description-es",
	 * "references-en", "pathToAbstract-en", "pathToDescription-es",
	 * "pathToReferences-en".
	 */
	private void loadPerLanguageSectionProperties() {
		Pattern langKey = Pattern
				.compile("^(abstract|description|references|introduction|pathToAbstract|pathToDescription|pathToReferences)-([A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})?)$");
		for (String key : propertyFile.stringPropertyNames()) {
			Matcher m = langKey.matcher(key);
			if (!m.matches()) {
				continue;
			}
			String value = propertyFile.getProperty(key, "");
			String lang = m.group(2);
			switch (m.group(1)) {
				case "abstract":
					abstractSectionByLang.put(lang, value);
					includeAbstract = true;
					break;
				case "description":
					descriptionSectionByLang.put(lang, value);
					break;
				case "references":
					referencesSectionByLang.put(lang, value);
					break;
				case "introduction":
					introductionSectionByLang.put(lang, value);
					break;
				case "pathToAbstract":
					abstractPathByLang.put(lang, value);
					break;
				case "pathToDescription":
					descriptionPathByLang.put(lang, value);
					break;
				case "pathToReferences":
					referencesPathByLang.put(lang, value);
					break;
			}
		}
	}

	/**
	 * EDINT extension: loads semicolon-separated lists of extra CSS/JS resources
	 * to be added to the head of the generated index documents.
	 */
	private void loadExtraResources() {
		extraCSS.clear();
		extraJS.clear();
		String css = propertyFile.getProperty(Constants.PF_EXTRA_CSS, "");
		if (!css.isEmpty()) {
			extraCSS.addAll(Arrays.asList(css.split(";")));
		}
		String js = propertyFile.getProperty(Constants.PF_EXTRA_JS, "");
		if (!js.isEmpty()) {
			extraJS.addAll(Arrays.asList(js.split(";")));
		}
	}

	public String getDescriptionPath() {
		return descriptionPath;
	}

	public String getIntroductionPath() {
		return introductionPath;
	}

	public String getOverviewPath() {
		return overviewPath;
	}

	public String getReferencesPath() {
		return referencesPath;
	}

	public List<File> getImports() {
		return this.imports;
	}

	public boolean isIncludeAbstract() {
		return includeAbstract;
	}

	public boolean isIncludeDescription() {
		return includeDescription;
	}

	public boolean isIncludeDiagram() {
		return includeDiagram;
	}

	public boolean isIncludeIntroduction() {
		return includeIntroduction;
	}

	public boolean isIncludeOverview() {
		return includeOverview;
	}

	public boolean isIncludeReferences() {
		return includeReferences;
	}

	public boolean isIncludeCrossReferenceSection() {
		return includeCrossReferenceSection;
	}

	public boolean isIncludeIndex() {
		return includeIndex;
	}

	public boolean isIncludeChangeLog() {
		return includeChangeLog;
	}

	public void setAbstractPath(String abstractPath) {
		this.abstractPath = abstractPath;
	}

	public void setDescriptionPath(String descriptionPath) {
		this.descriptionPath = descriptionPath;
	}

	public void setIncludeAbstract(boolean includeAbstract) {
		this.includeAbstract = includeAbstract;
	}

	public void setIncludeDescription(boolean includeDescription) {
		this.includeDescription = includeDescription;
	}

	public void setIncludeDiagram(boolean includeDiagram) {
		this.includeDiagram = includeDiagram;
	}

	public void setIncludeIntroduction(boolean includeIntroduction) {
		this.includeIntroduction = includeIntroduction;
	}

	public void setIncludeOverview(boolean includeOverview) {
		this.includeOverview = includeOverview;
	}

	public void setIncludeReferences(boolean includeReferences) {
		this.includeReferences = includeReferences;
	}

	public void setIncludeCrossReferenceSection(boolean includeCrossReferenceSection) {
		this.includeCrossReferenceSection = includeCrossReferenceSection;
	}

	public void setIncludeIndex(boolean includeIndex) {
		this.includeIndex = includeIndex;
	}

	public void setIncludeChangeLog(boolean includeChangeLog) {
		this.includeChangeLog = includeChangeLog;
	}

	public void setIntroductionPath(String introductionPath) {
		this.introductionPath = introductionPath;
	}

	public void setOverviewPath(String overviewPath) {
		this.overviewPath = overviewPath;
	}

	public void setPropertyFile(Properties propertyFile) {
		this.propertyFile = propertyFile;
	}

	public void setReferencesPath(String referencesPath) {
		this.referencesPath = referencesPath;
	}

	public void setFromFile(boolean fromFile) {
		this.fromFile = fromFile;
	}

	public String getCurrentLanguage() {
		return currentLanguage;
	}

	public boolean isUseImported() {
		return useImported;
	}

	public boolean isUseOwlAPI() {
		return useOwlAPI;
	}

	public boolean isUseReasoner() {
		return useReasoner;
	}

	public void setCurrentLanguage(String language) {
		this.currentLanguage = language;
	}

	public void setUseOwlAPI(boolean useOwlAPI) {
		this.useOwlAPI = useOwlAPI;
	}

	public void setUseImported(boolean useImported) {
		this.useImported = useImported;
	}

	public void setUseReasoner(boolean useReasoner) {
		this.useReasoner = useReasoner;
	}

	public Image getWidocoLogo() {
		if (logo == null) {
			loadWidocoLogos();
		}
		return this.logo;
	}

	public Image getWidocoLogoMini() {
		if (logoMini == null) {
			loadWidocoLogos();
		}
		return this.logoMini;
	}

	private void loadWidocoLogos() {
		try {
			this.logo = ImageIO.read(ClassLoader.getSystemResource("logo/logo2.png"));
			this.logoMini = ImageIO.read(ClassLoader.getSystemResource("logo/logomini100.png"));
		} catch (IOException e) {
			logger.error("Error loading the logo :( " + e.getMessage());
		}
	}

	public String getAbstractSection() {
		return abstractSection;
	}

	public void setAbstractSection(String abstractSection) {
		this.abstractSection = abstractSection;
	}

	public boolean isIncludeAnnotationProperties() {
		return includeAnnotationProperties;
	}

	public void setIncludeAnnotationProperties(boolean includeAnnotationProperties) {
		this.includeAnnotationProperties = includeAnnotationProperties;
	}

	public boolean isIncludeNamedIndividuals() {
		return includeNamedIndividuals;
	}

	public void setIncludeNamedIndividuals(boolean includeNamedIndividuals) {
		this.includeNamedIndividuals = includeNamedIndividuals;
	}

	public void addLanguageToGenerate(String lang) {
		if (!this.languages.containsKey(lang)) {
			this.languages.put(lang, false);
			if (currentLanguage.isEmpty()) {
				currentLanguage = lang;
			}
		}
	}

	public void removeLanguageToGenerate(String lang) {
		if (this.languages.containsKey(lang) && !languages.get(lang)) {
			this.languages.remove(lang);
			if (currentLanguage.equals(lang)) {
				ArrayList<String> aux = getRemainingToGenerateDoc();
				if (!aux.isEmpty()) {
					currentLanguage = getRemainingToGenerateDoc().get(0);
				} else {
					currentLanguage = "";
				}

			}
		}
	}

	public ArrayList<String> getLanguagesToGenerateDoc() {
		Iterator<String> it = languages.keySet().iterator();
		ArrayList<String> s = new ArrayList<String>();
		while (it.hasNext()) {
			s.add(it.next());
		}
		return s;
	}

	public ArrayList<String> getRemainingToGenerateDoc() {
		Iterator<String> it = languages.keySet().iterator();
		ArrayList<String> s = new ArrayList<String>();
		while (it.hasNext()) {
			String nextLang = it.next();
			if (!languages.get(nextLang)) {
				s.add(nextLang);
			}
		}
		return s;
	}

	public String getNextLanguageToGenerateDoc() {
		if (!"".equals(currentLanguage) && languages.get(currentLanguage)) {
			ArrayList<String> aux = getRemainingToGenerateDoc();
			if (aux.isEmpty()) {
				return null;
			} else {
				currentLanguage = aux.get(0);
			}
		}
		return currentLanguage;
	}

	public void vocabularySuccessfullyGenerated() {
		languages.put(currentLanguage, true);
		logger.info("Doc successfully generated for lang " + currentLanguage);
		currentLanguage = getNextLanguageToGenerateDoc();
	}

	public boolean isUseW3CStyle() {
		return useW3CStyle;
	}

	public void setUseW3CStyle(boolean useW3CStyle) {
		this.useW3CStyle = useW3CStyle;
	}

	public void setError(String error) {
		this.error = error;
	}

	public String getError() {
		return error;
	}

	public boolean isAddImportedOntologies() {
		return addImportedOntologies;
	}

	public void setAddImportedOntologies(boolean addImportedOntologies) {
		this.addImportedOntologies = addImportedOntologies;
	}

	public boolean isCreateHTACCESS() {
		return createHTACCESS;
	}

	public void setCreateHTACCESS(boolean createHTACCESS) {
		this.createHTACCESS = createHTACCESS;
	}

	public boolean isCreateWebVowlVisualization() {
		return createWebVowlVisualization;
	}

	public void setCreateWebVowlVisualization(boolean createWebVowlVisualization) {
		this.createWebVowlVisualization = createWebVowlVisualization;
	}

	public boolean isUseLicensius() {
		return useLicensius;
	}

	public void setUseLicensius(boolean useLicensius) {
		this.useLicensius = useLicensius;
	}

	/**
	 * True if serializations of the ontology will be displayed.
	 * 
	 * @return
	 */
	public boolean isDisplaySerializations() {
		return displaySerializations;
	}

	public void setDisplaySerializations(boolean displaySerializations) {
		this.displaySerializations = displaySerializations;
	}

	/**
	 * True if only the direct imports will be displayed.
	 * 
	 * @return
	 */
	public boolean isDisplayDirectImportsOnly() {
		return displayDirectImportsOnly;
	}

	public void setDisplayDirectImportsOnly(boolean displayDirectImportsOnly) {
		this.displayDirectImportsOnly = displayDirectImportsOnly;
	}

	/**
	 * returns the rewrite base path for .htaccess files (content negotiation)
	 * 
	 * @return
	 */
	public String getRewriteBase() {
		return rewriteBase;
	}

	public void setRewriteBase(String rewriteBase) {
		if (!rewriteBase.endsWith("/")) {
			rewriteBase += "/";
		}
		this.rewriteBase = rewriteBase;
	}

	public String getContextURI() {
		return contextURI;
	}

	public HashMap<String,String> getNamespaceDeclarations(){
		return namespaceDeclarations;
	}

	private void initializeImportedOntology(OWLOntology i) {
		i.getOntologyID().getOntologyIRI().ifPresent(iri -> initializeImportedOntology(iri, i, i));
	}

	/**
	 * EDINT extension: resolve the display name of an imported ontology from the
	 * OWL data only, in this order: (1) dcterms:title/rdfs:label of the loaded
	 * imported ontology, (2) a local dcterms:title/rdfs:label about the imported
	 * IRI declared in the importing document, (3) the last segment of the IRI.
	 */
	private void initializeImportedOntology(IRI iri, OWLOntology main, OWLOntology loaded) {
		String name = null;
		if (loaded != null) {
			name = getAnnotationLabel(loaded, iri);
		}
		if (isBlank(name)) {
			name = getAnnotationLabel(main, iri);
		}
		if (isBlank(name)) {
			String path = iri.toURI().getPath();
			String segment = path != null && path.contains("/")
					? path.substring(path.lastIndexOf('/') + 1)
					: iri.toString();
			if (segment.isEmpty()) {
				segment = iri.toString();
			}
			name = segment;
		}
		Ontology ont = new Ontology();
		ont.setNamespaceURI(iri.toString());
		ont.setName(name.replace("<", "&lt;").replace(">", "&gt;"));
		mainOntologyMetadata.getImportedOntologies().add(ont);
	}

	private String getAnnotationLabel(OWLOntology o, IRI subject) {
		String anyLang = null;
		for (OWLAnnotationAssertionAxiom a : o.annotationAssertionAxioms(subject).collect(java.util.stream.Collectors.toSet())) {
			String prop = a.getProperty().getIRI().getIRIString();
			if (Constants.PROP_DCTERMS_TITLE.equals(prop) || Constants.PROP_RDFS_LABEL.equals(prop)) {
				if (!a.getValue().isLiteral()) {
					continue;
				}
				String value = a.getValue().asLiteral().get().getLiteral();
				String lang = a.getValue().asLiteral().get().getLang();
				if (this.currentLanguage != null && this.currentLanguage.equals(lang)) {
					return value;
				}
				if (anyLang == null) {
					anyLang = value;
				}
			}
		}
		return anyLang;
	}

    public boolean isIncludeAllSectionsInOneDocument() {
        return includeAllSectionsInOneDocument;
    }

    public void setIncludeAllSectionsInOneDocument(boolean includeAllSectionsInOneDocument) {
        this.includeAllSectionsInOneDocument = includeAllSectionsInOneDocument;
    }

	public String getIntroText() {
		return introText;
	}

	public void setIntroText(String introText) {
		this.introText = introText;
	}
}
