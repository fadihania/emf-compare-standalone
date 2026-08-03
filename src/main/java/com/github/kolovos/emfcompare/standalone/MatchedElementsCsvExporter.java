package com.github.kolovos.emfcompare.standalone;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.Diff;
import org.eclipse.emf.compare.DifferenceKind;
import org.eclipse.emf.compare.Match;
import org.eclipse.emf.compare.ReferenceChange;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcorePackage;

/** Exports EMF Compare classifier and feature matches as grouped CSV rows. */
public final class MatchedElementsCsvExporter {

	private static final String MISSING = "--";
	private static final String HEADER =
		"row_type,modification,left_type,left_element,right_type,right_element\n";

	public String export(Comparison comparison) {
		Objects.requireNonNull(comparison, "comparison");
		ExportModel model = new ExportModel(comparison);
		List<ElementGroup> affected = model.groups.stream()
			.filter(ElementGroup::affected)
			.sorted(GROUP_ORDER)
			.toList();
		List<ElementGroup> unchanged = model.groups.stream()
			.filter(group -> !group.affected())
			.sorted(GROUP_ORDER)
			.toList();

		StringBuilder csv = new StringBuilder(HEADER);
		appendGroups(csv, affected);
		if (!affected.isEmpty() && !unchanged.isEmpty()) {
			csv.append('\n');
		}
		appendGroups(csv, unchanged);
		return csv.toString();
	}

	public void write(Comparison comparison, Path output) throws IOException {
		Objects.requireNonNull(output, "output");
		Path parent = output.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.writeString(output, export(comparison), StandardCharsets.UTF_8);
	}

	private static void appendGroups(StringBuilder csv, List<ElementGroup> groups) {
		for (ElementGroup group : groups) {
			appendRow(csv, group.element);
			group.features.stream().sorted(ROW_ORDER).forEach(row -> appendRow(csv, row));
		}
	}

	private static void appendRow(StringBuilder csv, Row row) {
		csv.append(csvField(row.rowType())).append(',')
			.append(csvField(row.modification().name())).append(',')
			.append(csvField(row.leftType())).append(',')
			.append(csvField(row.leftElement())).append(',')
			.append(csvField(row.rightType())).append(',')
			.append(csvField(row.rightElement())).append('\n');
	}

	private static final Comparator<Row> ROW_ORDER = Comparator
		.comparing((Row row) -> MISSING.equals(row.leftElement()))
		.thenComparing(Row::leftElement)
		.thenComparing(Row::rightElement)
		.thenComparing(Row::leftType)
		.thenComparing(Row::rightType);

	private static final Comparator<ElementGroup> GROUP_ORDER =
		(group1, group2) -> ROW_ORDER.compare(group1.element, group2.element);

	private enum Modification {
		NONE, ADDED, DELETED, CHANGED, MOVED
	}

	private static final class ElementGroup {
		private final Row element;
		private final List<Row> features = new ArrayList<>();

		private ElementGroup(Row element) {
			this.element = element;
		}

		private boolean affected() {
			return element.modification() != Modification.NONE
				|| features.stream().anyMatch(row -> row.modification() != Modification.NONE);
		}
	}

	private record Row(String rowType, Modification modification, String leftType,
		String leftElement, String rightType, String rightElement) {
	}

	private static final class ExportModel {
		private final Comparison comparison;
		private final Map<EObject, DifferenceKind> identityKinds = new IdentityHashMap<>();
		private final Map<EClassifier, ElementGroup> groupsByClassifier = new IdentityHashMap<>();
		private final List<ElementGroup> groups = new ArrayList<>();
		private final List<Match> featureMatches = new ArrayList<>();

		private ExportModel(Comparison comparison) {
			this.comparison = comparison;
			indexIdentityDiffs();
			for (Match match : comparison.getMatches()) {
				collectMatches(match);
			}
			attachFeatures();
		}

		private void indexIdentityDiffs() {
			for (Diff diff : comparison.getDifferences()) {
				if (diff instanceof ReferenceChange change
						&& isIdentityReference(change.getReference())
						&& (change.getValue() instanceof EClassifier
							|| change.getValue() instanceof EStructuralFeature)) {
					identityKinds.merge(change.getValue(), diff.getKind(),
						MatchedElementsCsvExporter::higherPriority);
				}
			}
		}

		private void collectMatches(Match match) {
			EObject left = match.getLeft();
			EObject right = match.getRight();
			if (left instanceof EClassifier || right instanceof EClassifier) {
				EClassifier leftClassifier = asClassifier(left);
				EClassifier rightClassifier = asClassifier(right);
				ElementGroup group = new ElementGroup(row("ELEMENT", match, leftClassifier, rightClassifier));
				groups.add(group);
				if (leftClassifier != null) {
					groupsByClassifier.put(leftClassifier, group);
				}
				if (rightClassifier != null) {
					groupsByClassifier.put(rightClassifier, group);
				}
			}
			if (left instanceof EStructuralFeature || right instanceof EStructuralFeature) {
				featureMatches.add(match);
			}
			for (Match submatch : match.getSubmatches()) {
				collectMatches(submatch);
			}
		}

		private void attachFeatures() {
			for (Match match : featureMatches) {
				EStructuralFeature left = asFeature(match.getLeft());
				EStructuralFeature right = asFeature(match.getRight());
				ElementGroup owner = left == null ? null : groupsByClassifier.get(left.getEContainingClass());
				if (owner == null && right != null) {
					owner = groupsByClassifier.get(right.getEContainingClass());
				}
				if (owner != null) {
					owner.features.add(row("FEATURE", match, left, right));
				}
			}
		}

		private Row row(String rowType, Match match, ENamedElement left, ENamedElement right) {
			return new Row(rowType, modification(match, left, right), typeName(left), qualifiedName(left),
				typeName(right), qualifiedName(right));
		}

		private Modification modification(Match match, EObject left, EObject right) {
			EObject existing = left != null ? left : right;
			DifferenceKind identityKind = identityKinds.get(existing);
			if (left == null) {
				return identityKind == DifferenceKind.MOVE ? Modification.MOVED : Modification.DELETED;
			}
			if (right == null) {
				return identityKind == DifferenceKind.MOVE ? Modification.MOVED : Modification.ADDED;
			}
			DifferenceKind otherIdentityKind = identityKinds.get(right);
			identityKind = higherPriority(identityKind, otherIdentityKind);
			if (identityKind == DifferenceKind.MOVE) {
				return Modification.MOVED;
			}

			DifferenceKind directKind = identityKind;
			for (Diff diff : match.getDifferences()) {
				if (!isChildIdentityDiff(diff)) {
					directKind = higherPriority(directKind, diff.getKind());
				}
			}
			if (directKind == null) {
				return Modification.NONE;
			}
			return directKind == DifferenceKind.MOVE ? Modification.MOVED : Modification.CHANGED;
		}
	}

	private static boolean isChildIdentityDiff(Diff diff) {
		return diff instanceof ReferenceChange change && isIdentityReference(change.getReference());
	}

	private static boolean isIdentityReference(EStructuralFeature reference) {
		return reference == EcorePackage.Literals.EPACKAGE__ECLASSIFIERS
			|| reference == EcorePackage.Literals.ECLASS__ESTRUCTURAL_FEATURES;
	}

	private static DifferenceKind higherPriority(DifferenceKind first, DifferenceKind second) {
		if (first == null) {
			return second;
		}
		if (second == null) {
			return first;
		}
		return priority(first) >= priority(second) ? first : second;
	}

	private static int priority(DifferenceKind kind) {
		return switch (kind) {
			case MOVE -> 4;
			case CHANGE -> 3;
			case ADD -> 2;
			case DELETE -> 1;
		};
	}

	private static EClassifier asClassifier(EObject object) {
		return object instanceof EClassifier classifier ? classifier : null;
	}

	private static EStructuralFeature asFeature(EObject object) {
		return object instanceof EStructuralFeature feature ? feature : null;
	}

	private static String typeName(ENamedElement element) {
		return element == null ? MISSING : element.eClass().getName();
	}

	private static String qualifiedName(ENamedElement element) {
		if (element == null) {
			return MISSING;
		}
		List<String> names = new ArrayList<>();
		EObject current = element;
		while (current != null) {
			if (current instanceof ENamedElement named
					&& named.getName() != null && !named.getName().isBlank()) {
				names.add(0, named.getName());
			}
			if (current instanceof EStructuralFeature feature) {
				current = feature.getEContainingClass();
			} else if (current instanceof EClassifier classifier) {
				current = classifier.getEPackage();
			} else if (current instanceof EPackage ePackage) {
				current = ePackage.getESuperPackage();
			} else {
				current = current.eContainer();
			}
		}
		return String.join(".", names);
	}

	private static String csvField(String value) {
		return '"' + value.replace("\"", "\"\"") + '"';
	}
}
