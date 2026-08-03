package com.github.kolovos.emfcompare.standalone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.emf.compare.AttributeChange;
import org.eclipse.emf.compare.CompareFactory;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.DifferenceKind;
import org.eclipse.emf.compare.Match;
import org.eclipse.emf.compare.ReferenceChange;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MatchedElementsCsvExporterTest {

	private static final CompareFactory COMPARE = CompareFactory.eINSTANCE;
	private static final EcoreFactory ECORE = EcoreFactory.eINSTANCE;

	@Test
	void groupsFeaturesAndOrdersSectionsByLeftElement() {
		String csv = new MatchedElementsCsvExporter().export(comparison());
		List<String> lines = csv.lines().toList();

		assertEquals("row_type,modification,left_type,left_element,right_type,right_element", lines.get(0));
		assertRow(lines.get(1), "ELEMENT", "ADDED", "EClass", "model.Alpha", "--", "--");
		assertRow(lines.get(2), "FEATURE", "ADDED", "EAttribute", "model.Alpha.alphaFeature", "--", "--");
		assertRow(lines.get(3), "ELEMENT", "CHANGED", "EClass", "model.Beta", "EClass", "model.Beta");
		assertRow(lines.get(4), "FEATURE", "ADDED", "EAttribute", "model.Beta.added", "--", "--");
		assertRow(lines.get(5), "FEATURE", "CHANGED", "EAttribute", "model.Beta.changed",
			"EAttribute", "model.Beta.changed");
		assertRow(lines.get(6), "FEATURE", "MOVED", "EAttribute", "model.Beta.moved",
			"EAttribute", "model.OldOwner.moved");
		assertRow(lines.get(7), "FEATURE", "NONE", "EAttribute", "model.Beta.stable",
			"EAttribute", "model.Beta.stable");
		assertRow(lines.get(8), "FEATURE", "DELETED", "--", "--",
			"EAttribute", "model.Beta.deleted");
		assertRow(lines.get(9), "ELEMENT", "MOVED", "EClass", "model.Epsilon",
			"EClass", "model.Epsilon");
		assertRow(lines.get(10), "ELEMENT", "NONE", "EClass", "model.FeatureOwner",
			"EClass", "model.FeatureOwner");
		assertRow(lines.get(11), "FEATURE", "ADDED", "EAttribute", "model.FeatureOwner.newFeature",
			"--", "--");
		assertRow(lines.get(12), "ELEMENT", "DELETED", "--", "--", "EClass", "model.Delta");
		assertEquals("", lines.get(13));
		assertRow(lines.get(14), "ELEMENT", "NONE", "EClass", "model.Gamma", "EClass", "model.Gamma");
		assertRow(lines.get(15), "FEATURE", "NONE", "EAttribute", "model.Gamma.name",
			"EAttribute", "model.Gamma.name");
	}

	@Test
	void writesUtf8Deterministically(@TempDir Path directory) throws IOException {
		Comparison comparison = comparison();
		MatchedElementsCsvExporter exporter = new MatchedElementsCsvExporter();
		Path output = directory.resolve("nested/matching-elements.csv");

		exporter.write(comparison, output);

		assertEquals(exporter.export(comparison), Files.readString(output));
		assertTrue(Files.readString(output).contains("model.Beta.moved"));
	}

	@Test
	void escapesCsvSensitiveQualifiedNames() {
		EPackage leftPackage = ePackage("mo,del");
		EPackage rightPackage = ePackage("mo,del");
		EClass leftClass = eClass(leftPackage, "Quo\"te");
		EClass rightClass = eClass(rightPackage, "Quo\"te");
		Comparison comparison = COMPARE.createComparison();
		Match packageMatch = match(leftPackage, rightPackage);
		comparison.getMatches().add(packageMatch);
		packageMatch.getSubmatches().add(match(leftClass, rightClass));

		String csv = new MatchedElementsCsvExporter().export(comparison);

		assertTrue(csv.contains("\"mo,del.Quo\"\"te\""));
	}

	private static Comparison comparison() {
		EPackage leftPackage = ePackage("model");
		EPackage rightPackage = ePackage("model");
		EClass alpha = eClass(leftPackage, "Alpha");
		EClass leftBeta = eClass(leftPackage, "Beta");
		EClass rightBeta = eClass(rightPackage, "Beta");
		EClass rightDelta = eClass(rightPackage, "Delta");
		EClass leftEpsilon = eClass(leftPackage, "Epsilon");
		EClass rightEpsilon = eClass(rightPackage, "Epsilon");
		EClass leftFeatureOwner = eClass(leftPackage, "FeatureOwner");
		EClass rightFeatureOwner = eClass(rightPackage, "FeatureOwner");
		EClass leftGamma = eClass(leftPackage, "Gamma");
		EClass rightGamma = eClass(rightPackage, "Gamma");
		EClass rightOldOwner = eClass(rightPackage, "OldOwner");

		EAttribute alphaFeature = attribute(alpha, "alphaFeature");
		EAttribute betaAdded = attribute(leftBeta, "added");
		EAttribute leftChanged = attribute(leftBeta, "changed");
		EAttribute rightChanged = attribute(rightBeta, "changed");
		EAttribute leftMoved = attribute(leftBeta, "moved");
		EAttribute rightMoved = attribute(rightOldOwner, "moved");
		EAttribute leftStable = attribute(leftBeta, "stable");
		EAttribute rightStable = attribute(rightBeta, "stable");
		EAttribute deleted = attribute(rightBeta, "deleted");
		EAttribute newFeature = attribute(leftFeatureOwner, "newFeature");
		EAttribute leftGammaName = attribute(leftGamma, "name");
		EAttribute rightGammaName = attribute(rightGamma, "name");

		Comparison comparison = COMPARE.createComparison();
		Match packageMatch = match(leftPackage, rightPackage);
		Match alphaMatch = match(alpha, null);
		Match betaMatch = match(leftBeta, rightBeta);
		Match epsilonMatch = match(leftEpsilon, rightEpsilon);
		Match featureOwnerMatch = match(leftFeatureOwner, rightFeatureOwner);
		Match gammaMatch = match(leftGamma, rightGamma);
		Match deltaMatch = match(null, rightDelta);
		comparison.getMatches().add(packageMatch);
		packageMatch.getSubmatches().addAll(
			List.of(alphaMatch, betaMatch, epsilonMatch, featureOwnerMatch, gammaMatch, deltaMatch));

		alphaMatch.getSubmatches().add(match(alphaFeature, null));
		Match changedMatch = match(leftChanged, rightChanged);
		betaMatch.getSubmatches().addAll(List.of(
			match(betaAdded, null), changedMatch, match(leftMoved, rightMoved),
			match(leftStable, rightStable), match(null, deleted)));
		featureOwnerMatch.getSubmatches().add(match(newFeature, null));
		gammaMatch.getSubmatches().add(match(leftGammaName, rightGammaName));

		packageMatch.getDifferences().add(referenceChange(DifferenceKind.ADD,
			EcorePackage.Literals.EPACKAGE__ECLASSIFIERS, alpha));
		packageMatch.getDifferences().add(referenceChange(DifferenceKind.DELETE,
			EcorePackage.Literals.EPACKAGE__ECLASSIFIERS, rightDelta));
		packageMatch.getDifferences().add(referenceChange(DifferenceKind.MOVE,
			EcorePackage.Literals.EPACKAGE__ECLASSIFIERS, leftEpsilon));
		betaMatch.getDifferences().add(attributeChange(DifferenceKind.CHANGE,
			EcorePackage.Literals.ECLASS__ABSTRACT, true));
		betaMatch.getDifferences().add(referenceChange(DifferenceKind.ADD,
			EcorePackage.Literals.ECLASS__ESTRUCTURAL_FEATURES, betaAdded));
		betaMatch.getDifferences().add(referenceChange(DifferenceKind.MOVE,
			EcorePackage.Literals.ECLASS__ESTRUCTURAL_FEATURES, leftMoved));
		betaMatch.getDifferences().add(referenceChange(DifferenceKind.DELETE,
			EcorePackage.Literals.ECLASS__ESTRUCTURAL_FEATURES, deleted));
		changedMatch.getDifferences().add(attributeChange(DifferenceKind.CHANGE,
			EcorePackage.Literals.ETYPED_ELEMENT__UPPER_BOUND, 2));
		featureOwnerMatch.getDifferences().add(referenceChange(DifferenceKind.ADD,
			EcorePackage.Literals.ECLASS__ESTRUCTURAL_FEATURES, newFeature));
		return comparison;
	}

	private static void assertRow(String line, String... fields) {
		assertEquals(String.join(",", java.util.Arrays.stream(fields)
			.map(field -> "\"" + field + "\"").toList()), line);
	}

	private static EPackage ePackage(String name) {
		EPackage ePackage = ECORE.createEPackage();
		ePackage.setName(name);
		return ePackage;
	}

	private static EClass eClass(EPackage owner, String name) {
		EClass eClass = ECORE.createEClass();
		eClass.setName(name);
		owner.getEClassifiers().add(eClass);
		return eClass;
	}

	private static EAttribute attribute(EClass owner, String name) {
		EAttribute attribute = ECORE.createEAttribute();
		attribute.setName(name);
		attribute.setEType(EcorePackage.Literals.ESTRING);
		owner.getEStructuralFeatures().add(attribute);
		return attribute;
	}

	private static Match match(EObject left, EObject right) {
		Match match = COMPARE.createMatch();
		match.setLeft(left);
		match.setRight(right);
		return match;
	}

	private static ReferenceChange referenceChange(
		DifferenceKind kind, org.eclipse.emf.ecore.EReference reference, EObject value) {
		ReferenceChange change = COMPARE.createReferenceChange();
		change.setKind(kind);
		change.setReference(reference);
		change.setValue(value);
		return change;
	}

	private static AttributeChange attributeChange(
		DifferenceKind kind, EAttribute attribute, Object value) {
		AttributeChange change = COMPARE.createAttributeChange();
		change.setKind(kind);
		change.setAttribute(attribute);
		change.setValue(value);
		return change;
	}
}
