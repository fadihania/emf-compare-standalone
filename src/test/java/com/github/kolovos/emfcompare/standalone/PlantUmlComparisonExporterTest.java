package com.github.kolovos.emfcompare.standalone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.compare.AttributeChange;
import org.eclipse.emf.compare.CompareFactory;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.DifferenceKind;
import org.eclipse.emf.compare.EMFCompare;
import org.eclipse.emf.compare.Match;
import org.eclipse.emf.compare.ReferenceChange;
import org.eclipse.emf.compare.scope.DefaultComparisonScope;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlantUmlComparisonExporterTest {

	private static final CompareFactory COMPARE = CompareFactory.eINSTANCE;
	private static final EcoreFactory ECORE = EcoreFactory.eINSTANCE;

	@Test
	void rendersExactAddDeleteAndChangeKindsWithoutNotes() {
		Comparison comparison = customerComparison();

		String plantUml = new PlantUmlComparisonExporter().export(comparison);

		assertTrue(plantUml.contains("class \"Customer\" as C_library_Customer <<CHANGED>> #FDF8E2"));
		assertTrue(plantUml.contains("<color:#8F5F00><b>~ displayName:</b> EString ⇒ "
			+ "<b>displayName:</b> LocalizedString</color>"));
		assertTrue(plantUml.contains("<color:#16803A><b>+ preferredCurrency:</b> Currency</color>"));
		assertTrue(plantUml.contains("<color:#B42318><s><b>- legacyCode:</b> EString</s></color>"));
		assertTrue(plantUml.contains("class \"Invoice\" as C_library_Invoice <<ADDED>> #D5F5E3"));
		assertTrue(plantUml.contains("class \"LegacyRecord\" as C_library_LegacyRecord <<DELETED>> #FADBD8"));
		assertTrue(plantUml.contains("class \"Country\" as C_library_Country {"));
		assertFalse(plantUml.contains("class \"Unaffected\""));

		assertTrue(plantUml.contains("-[#16803A]-> \"0..*\" C_library_Invoice : "
			+ "<color:#16803A>+ invoices</color>"));
		assertTrue(plantUml.contains("-[#B42318,dashed]-> \"0..1\" C_library_LegacyRecord : "
			+ "<color:#B42318><s>- legacyRecord</s></color>"));
		assertTrue(plantUml.contains("-[#8F5F00]-> "
			+ "\"<color:#8F5F00><s>0..1</s> ⇒ 1\" C_library_Country : "
			+ "<color:#8F5F00>~ country</color>"));
		assertFalse(plantUml.contains("note "));
		assertFalse(plantUml.contains("unnamed"));
	}

	@Test
	void keepsMoveInCurrentOwnerAndDoesNotPromoteChangedSubclassToAdded() {
		Comparison comparison = movedFeatureComparison();

		String plantUml = new PlantUmlComparisonExporter().export(comparison);
		String student = classBlock(plantUml, "Student");
		String user = classBlock(plantUml, "User");

		assertTrue(plantUml.contains("class \"Student:\\n<color:#16803A><b>+ User</b></color>\" "
			+ "as C_library_Student <<CHANGED>> #FDF8E2"));
		assertFalse(plantUml.contains("as C_library_Student <<ADDED>>"));
		assertTrue(plantUml.contains("class \"User\" as C_library_User <<ADDED>> #D5F5E3"));
		assertTrue(user.contains("<color:#8F5F00><b>~ forename:</b> EString "
			+ "<i>(Student ⇒ User)</i></color>"));
		assertTrue(user.contains("<color:#16803A><b>+ identifier:</b> EInt</color>"));
		assertFalse(student.contains("forename"));
		assertTrue(student.contains("<color:#B42318><s><b>- legacyCode:</b> EString</s></color>"));
		assertEquals(1, occurrences(plantUml, "<b>~ forename:</b>"));
		assertTrue(plantUml.contains("C_library_User <|-[#16803A]-- C_library_Student : "
			+ "<color:#16803A>+ extends</color>"));
		assertTrue(plantUml.contains("<color:#8F5F00>Moved"));
	}

	@Test
	void canDisableExtensionRelationshipsWhileKeepingSuperclassDetails() {
		Comparison comparison = movedFeatureComparison();
		PlantUmlComparisonExporter exporter = new PlantUmlComparisonExporter();

		String withRelationships = exporter.export(comparison, true);
		String withoutRelationships = exporter.export(comparison, false);

		assertTrue(withRelationships.contains("C_library_User <|-[#16803A]-- C_library_Student"));
		assertFalse(withoutRelationships.contains("<|-["));
		assertTrue(withoutRelationships.contains(
			"class \"Student:\\n<color:#16803A><b>+ User</b></color>\""));
	}

	@Test
	void suppressesTechnicalPropertyDiffForAddedFeature() {
		Comparison comparison = customerComparison();

		String plantUml = new PlantUmlComparisonExporter().export(comparison);
		String customer = classBlock(plantUml, "Customer");

		assertEquals(1, occurrences(customer, "preferredCurrency"));
		assertTrue(customer.contains("<b>+ preferredCurrency:</b>"));
		assertFalse(customer.contains("<b>~ preferredCurrency:</b>"));
	}

	@Test
	void rendersExplicitClassRenameWithCurrentNameFirst() {
		Comparison comparison = renamedClassComparison("Client", "Customer");

		String plantUml = new PlantUmlComparisonExporter().export(comparison);

		assertTrue(plantUml.contains("class \"<color:#8F5F00><b>Client (Customer)</b></color>\" "
			+ "as C_library_Client <<CHANGED>> #FDF8E2"));
		assertFalse(plantUml.contains("class \"Customer\" as C_library_Customer"));
	}

	@Test
	void escapesRenamedClassNames() {
		Comparison comparison = renamedClassComparison("Client & Core", "Customer <Legacy>");

		String plantUml = new PlantUmlComparisonExporter().export(comparison);

		assertTrue(plantUml.contains("<b>Client &amp; Core (Customer &lt;Legacy&gt;)</b>"));
		assertTrue(plantUml.contains("as C_library_Client___Core <<CHANGED>>"));
	}

	@Test
	void rendersTheThesisRegressionFromRealEmfCompare() throws IOException {
		Resource current = loadResource(
			"/models/semantic-model-comparison/thesis-system2.ecore",
			"platform:/resource/models/semantic-model-comparison/thesis-system2.ecore");
		Resource old = loadResource(
			"/models/semantic-model-comparison/thesis-system1.ecore",
			"platform:/resource/models/semantic-model-comparison/thesis-system1.ecore");
		Comparison comparison = EMFCompare.builder().build()
			.compare(new DefaultComparisonScope(current, old, null));

		String plantUml = new PlantUmlComparisonExporter().export(comparison);
		String student = classBlock(plantUml, "Student");
		String teachingStaff = classBlock(plantUml, "TeachingStaffMember");
		String user = classBlock(plantUml, "User");
		String thesis = classBlock(plantUml, "Thesis");

		assertTrue(plantUml.contains("class \"User\" as C_thesis_User <<ADDED>> #D5F5E3"));
		assertTrue(plantUml.contains("title semantic-model-comparison Ecore Models Comparison"));
		assertTrue(plantUml.contains("skinparam titleFontSize 26"));
		assertTrue(student.contains("as C_thesis_Student <<CHANGED>> #FDF8E2"));
		assertTrue(teachingStaff.contains("as C_thesis_TeachingStaffMember <<CHANGED>> #FDF8E2"));
		assertTrue(student.contains("<color:#16803A><b>+ User</b></color>"));
		assertTrue(teachingStaff.contains("<color:#16803A><b>+ User</b></color>"));
		assertFalse(student.contains("forename"));
		assertFalse(student.contains("surname"));
		assertTrue(user.contains("⇒ User"));
		assertTrue(student.contains("<b>- emailAddress:</b>"));
		assertTrue(teachingStaff.contains("<b>- identifier:</b>"));
		assertTrue(thesis.contains("<b>+ subject:</b>"));
		assertTrue(thesis.contains("<b>- topic:</b>"));
		assertFalse(plantUml.contains("note "));
		assertFalse(plantUml.contains("unnamed"));
	}

	@Test
	void writesUtf8Deterministically(@TempDir Path tempDirectory) throws IOException {
		Comparison comparison = customerComparison();
		PlantUmlComparisonExporter exporter = new PlantUmlComparisonExporter();
		Path output = tempDirectory.resolve("nested/comparison.puml");

		exporter.write(comparison, output);

		assertEquals(exporter.export(comparison), Files.readString(output));
		assertTrue(Files.readString(output).contains("⇒"));
	}

	private static Comparison customerComparison() {
		EPackage currentPackage = ePackage("library");
		EPackage oldPackage = ePackage("library");
		EClass currentCustomer = addClass(currentPackage, "Customer");
		EClass oldCustomer = addClass(oldPackage, "Customer");
		EClass currentCountry = addClass(currentPackage, "Country");
		EClass oldCountry = addClass(oldPackage, "Country");
		EClass currentInvoice = addClass(currentPackage, "Invoice");
		EClass oldLegacy = addClass(oldPackage, "LegacyRecord");
		addClass(currentPackage, "Unaffected");
		addClass(oldPackage, "Unaffected");

		EDataType localizedString = dataType("LocalizedString");
		EDataType currency = dataType("Currency");
		currentPackage.getEClassifiers().add(localizedString);
		currentPackage.getEClassifiers().add(currency);

		EAttribute currentId = addAttribute(currentCustomer, "id", EcorePackage.Literals.ESTRING);
		EAttribute oldId = addAttribute(oldCustomer, "id", EcorePackage.Literals.ESTRING);
		EAttribute currentDisplayName = addAttribute(currentCustomer, "displayName", localizedString);
		EAttribute oldDisplayName = addAttribute(oldCustomer, "displayName", EcorePackage.Literals.ESTRING);
		EAttribute preferredCurrency = addAttribute(currentCustomer, "preferredCurrency", currency);
		EAttribute legacyCode = addAttribute(oldCustomer, "legacyCode", EcorePackage.Literals.ESTRING);
		addAttribute(currentInvoice, "number", EcorePackage.Literals.ESTRING);
		addAttribute(currentInvoice, "total", EcorePackage.Literals.EDOUBLE);
		addAttribute(oldLegacy, "code", EcorePackage.Literals.ESTRING);

		EReference invoices = addReference(currentCustomer, "invoices", currentInvoice, 0, -1);
		EReference legacyRecord = addReference(oldCustomer, "legacyRecord", oldLegacy, 0, 1);
		EReference currentCountryReference = addReference(currentCustomer, "country", currentCountry, 1, 1);
		EReference oldCountryReference = addReference(oldCustomer, "country", oldCountry, 0, 1);

		Comparison comparison = COMPARE.createComparison();
		Match packageMatch = match(currentPackage, oldPackage);
		Match customerMatch = match(currentCustomer, oldCustomer);
		comparison.getMatches().add(packageMatch);
		packageMatch.getSubmatches().add(customerMatch);
		packageMatch.getSubmatches().add(match(currentCountry, oldCountry));
		packageMatch.getSubmatches().add(match(currentInvoice, null));
		packageMatch.getSubmatches().add(match(null, oldLegacy));
		customerMatch.getSubmatches().add(match(currentId, oldId));
		Match displayNameMatch = match(currentDisplayName, oldDisplayName);
		customerMatch.getSubmatches().add(displayNameMatch);
		Match preferredCurrencyMatch = match(preferredCurrency, null);
		customerMatch.getSubmatches().add(preferredCurrencyMatch);
		customerMatch.getSubmatches().add(match(null, legacyCode));
		customerMatch.getSubmatches().add(match(invoices, null));
		customerMatch.getSubmatches().add(match(null, legacyRecord));
		Match countryReferenceMatch = match(currentCountryReference, oldCountryReference);
		customerMatch.getSubmatches().add(countryReferenceMatch);

		packageMatch.getDifferences().add(referenceChange(DifferenceKind.ADD,
			EcorePackage.Literals.EPACKAGE__ECLASSIFIERS, currentInvoice));
		packageMatch.getDifferences().add(referenceChange(DifferenceKind.DELETE,
			EcorePackage.Literals.EPACKAGE__ECLASSIFIERS, oldLegacy));
		customerMatch.getDifferences().add(referenceChange(DifferenceKind.ADD,
			EcorePackage.Literals.ECLASS__ESTRUCTURAL_FEATURES, preferredCurrency));
		customerMatch.getDifferences().add(referenceChange(DifferenceKind.DELETE,
			EcorePackage.Literals.ECLASS__ESTRUCTURAL_FEATURES, legacyCode));
		customerMatch.getDifferences().add(referenceChange(DifferenceKind.ADD,
			EcorePackage.Literals.ECLASS__ESTRUCTURAL_FEATURES, invoices));
		customerMatch.getDifferences().add(referenceChange(DifferenceKind.DELETE,
			EcorePackage.Literals.ECLASS__ESTRUCTURAL_FEATURES, legacyRecord));
		displayNameMatch.getDifferences().add(referenceChange(DifferenceKind.CHANGE,
			EcorePackage.Literals.ETYPED_ELEMENT__ETYPE, localizedString));
		preferredCurrencyMatch.getDifferences().add(referenceChange(DifferenceKind.CHANGE,
			EcorePackage.Literals.ETYPED_ELEMENT__ETYPE, currency));
		countryReferenceMatch.getDifferences().add(attributeChange(DifferenceKind.CHANGE,
			EcorePackage.Literals.ETYPED_ELEMENT__LOWER_BOUND, 1));
		return comparison;
	}

	private static Comparison movedFeatureComparison() {
		EPackage currentPackage = ePackage("library");
		EPackage oldPackage = ePackage("library");
		EClass currentUser = addClass(currentPackage, "User");
		EClass currentStudent = addClass(currentPackage, "Student");
		EClass oldStudent = addClass(oldPackage, "Student");
		currentStudent.getESuperTypes().add(currentUser);

		EAttribute currentIdentifier = addAttribute(currentUser, "identifier", EcorePackage.Literals.EINT);
		EAttribute currentForename = addAttribute(currentUser, "forename", EcorePackage.Literals.ESTRING);
		EAttribute oldForename = addAttribute(oldStudent, "forename", EcorePackage.Literals.ESTRING);
		EAttribute oldLegacy = addAttribute(oldStudent, "legacyCode", EcorePackage.Literals.ESTRING);

		Comparison comparison = COMPARE.createComparison();
		Match packageMatch = match(currentPackage, oldPackage);
		Match userMatch = match(currentUser, null);
		Match studentMatch = match(currentStudent, oldStudent);
		Match identifierMatch = match(currentIdentifier, null);
		Match forenameMatch = match(currentForename, oldForename);
		Match legacyMatch = match(null, oldLegacy);
		comparison.getMatches().add(packageMatch);
		packageMatch.getSubmatches().add(userMatch);
		packageMatch.getSubmatches().add(studentMatch);
		userMatch.getSubmatches().add(identifierMatch);
		userMatch.getSubmatches().add(forenameMatch);
		studentMatch.getSubmatches().add(legacyMatch);

		packageMatch.getDifferences().add(referenceChange(DifferenceKind.ADD,
			EcorePackage.Literals.EPACKAGE__ECLASSIFIERS, currentUser));
		userMatch.getDifferences().add(referenceChange(DifferenceKind.ADD,
			EcorePackage.Literals.ECLASS__ESTRUCTURAL_FEATURES, currentIdentifier));
		userMatch.getDifferences().add(referenceChange(DifferenceKind.MOVE,
			EcorePackage.Literals.ECLASS__ESTRUCTURAL_FEATURES, currentForename));
		studentMatch.getDifferences().add(referenceChange(DifferenceKind.DELETE,
			EcorePackage.Literals.ECLASS__ESTRUCTURAL_FEATURES, oldLegacy));
		studentMatch.getDifferences().add(referenceChange(DifferenceKind.ADD,
			EcorePackage.Literals.ECLASS__ESUPER_TYPES, currentUser));
		return comparison;
	}

	private static Comparison renamedClassComparison(String currentName, String oldName) {
		EPackage currentPackage = ePackage("library");
		EPackage oldPackage = ePackage("library");
		EClass currentClass = addClass(currentPackage, currentName);
		EClass oldClass = addClass(oldPackage, oldName);
		addAttribute(currentClass, "id", EcorePackage.Literals.ESTRING);
		addAttribute(oldClass, "id", EcorePackage.Literals.ESTRING);

		Comparison comparison = COMPARE.createComparison();
		Match packageMatch = match(currentPackage, oldPackage);
		Match classMatch = match(currentClass, oldClass);
		comparison.getMatches().add(packageMatch);
		packageMatch.getSubmatches().add(classMatch);
		classMatch.getDifferences().add(attributeChange(DifferenceKind.CHANGE,
			EcorePackage.Literals.ENAMED_ELEMENT__NAME, currentName));
		return comparison;
	}

	private static Resource loadResource(String classpathLocation, String uri) throws IOException {
		ResourceSet resourceSet = new ResourceSetImpl();
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
		Resource resource = resourceSet.createResource(URI.createURI(uri));
		try (InputStream input = PlantUmlComparisonExporterTest.class.getResourceAsStream(classpathLocation)) {
			if (input == null) {
				throw new IOException("Missing test resource: " + classpathLocation);
			}
			resource.load(input, null);
		}
		return resource;
	}

	private static String classBlock(String plantUml, String className) {
		int start = plantUml.indexOf("class \"" + className);
		if (start < 0) {
			return "";
		}
		int end = plantUml.indexOf("\n}\n", start);
		return end < 0 ? plantUml.substring(start) : plantUml.substring(start, end + 3);
	}

	private static int occurrences(String text, String value) {
		return (text.length() - text.replace(value, "").length()) / value.length();
	}

	private static Match match(EObject current, EObject old) {
		Match match = COMPARE.createMatch();
		match.setLeft(current);
		match.setRight(old);
		return match;
	}

	private static ReferenceChange referenceChange(DifferenceKind kind, EReference reference, EObject value) {
		ReferenceChange change = COMPARE.createReferenceChange();
		change.setKind(kind);
		change.setReference(reference);
		change.setValue(value);
		return change;
	}

	private static AttributeChange attributeChange(DifferenceKind kind, EAttribute attribute, Object value) {
		AttributeChange change = COMPARE.createAttributeChange();
		change.setKind(kind);
		change.setAttribute(attribute);
		change.setValue(value);
		return change;
	}

	private static EPackage ePackage(String name) {
		EPackage ePackage = ECORE.createEPackage();
		ePackage.setName(name);
		ePackage.setNsPrefix(name);
		ePackage.setNsURI("urn:" + name);
		return ePackage;
	}

	private static EClass addClass(EPackage ePackage, String name) {
		EClass eClass = ECORE.createEClass();
		eClass.setName(name);
		ePackage.getEClassifiers().add(eClass);
		return eClass;
	}

	private static EDataType dataType(String name) {
		EDataType dataType = ECORE.createEDataType();
		dataType.setName(name);
		dataType.setInstanceClass(String.class);
		return dataType;
	}

	private static EAttribute addAttribute(EClass owner, String name, EDataType type) {
		EAttribute attribute = ECORE.createEAttribute();
		attribute.setName(name);
		attribute.setEType(type);
		owner.getEStructuralFeatures().add(attribute);
		return attribute;
	}

	private static EReference addReference(EClass owner, String name, EClass type, int lower, int upper) {
		EReference reference = ECORE.createEReference();
		reference.setName(name);
		reference.setEType(type);
		reference.setLowerBound(lower);
		reference.setUpperBound(upper);
		owner.getEStructuralFeatures().add(reference);
		return reference;
	}
}
