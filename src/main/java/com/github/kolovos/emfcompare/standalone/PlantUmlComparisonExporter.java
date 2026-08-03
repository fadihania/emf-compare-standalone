package com.github.kolovos.emfcompare.standalone;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.emf.compare.AttributeChange;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.Diff;
import org.eclipse.emf.compare.DifferenceKind;
import org.eclipse.emf.compare.Match;
import org.eclipse.emf.compare.ReferenceChange;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.ETypedElement;
import org.eclipse.emf.ecore.EcorePackage;

/** Exports an Ecore {@link Comparison} as a focused PlantUML class diagram. */
public final class PlantUmlComparisonExporter {

	private static final String ADDED_TEXT = "#16803A";
	private static final String DELETED_TEXT = "#B42318";
	private static final String CHANGED_TEXT = "#8F5F00";
	private static final String ADDED_BACKGROUND = "#D5F5E3";
	private static final String DELETED_BACKGROUND = "#FADBD8";
	private static final String CHANGED_BACKGROUND = "#FDF8E2";

	public String export(Comparison comparison) {
		Objects.requireNonNull(comparison, "comparison");
		return new Diagram(comparison).render();
	}

	public void write(Comparison comparison, Path output) throws IOException {
		Objects.requireNonNull(output, "output");
		Path parent = output.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.writeString(output, export(comparison), StandardCharsets.UTF_8);
	}

	private enum Kind {
		NONE, ADD, DELETE, CHANGE, MOVE;

		private static Kind from(DifferenceKind kind) {
			return switch (kind) {
				case ADD -> ADD;
				case DELETE -> DELETE;
				case CHANGE -> CHANGE;
				case MOVE -> MOVE;
			};
		}
	}

	private static final class ClassView {
		private EClass current;
		private EClass old;
		private Kind identityKind = Kind.NONE;
		private boolean affected;
		private String alias;

		private EClass displayed() {
			if (identityKind == Kind.DELETE) {
				return old != null ? old : current;
			}
			return current != null ? current : old;
		}

		private Kind presentationKind() {
			if (identityKind != Kind.NONE) {
				return identityKind;
			}
			return affected ? Kind.CHANGE : Kind.NONE;
		}
	}

	private static final class FeatureView {
		private EStructuralFeature current;
		private EStructuralFeature old;
		private ClassView currentOwner;
		private ClassView oldOwner;
		private Kind structuralKind = Kind.NONE;
		private final List<Diff> propertyDiffs = new ArrayList<>();

		private EStructuralFeature displayed() {
			if (structuralKind == Kind.DELETE) {
				return old != null ? old : current;
			}
			return current != null ? current : old;
		}

		private ClassView renderOwner() {
			return structuralKind == Kind.DELETE
				? firstNonNull(oldOwner, currentOwner)
				: firstNonNull(currentOwner, oldOwner);
		}

		private Kind presentationKind() {
			if (structuralKind != Kind.NONE) {
				return structuralKind;
			}
			if (current != null && old != null && !propertyDiffs.isEmpty()) {
				return Kind.CHANGE;
			}
			ClassView owner = renderOwner();
			if (owner != null && owner.identityKind == Kind.ADD && current != null) {
				return Kind.ADD;
			}
			if (owner != null && owner.identityKind == Kind.DELETE && old != null) {
				return Kind.DELETE;
			}
			return Kind.NONE;
		}
	}

	private record InheritanceDiff(Diff diff, ClassView subclass, ClassView superclass, Kind kind) {
	}

	private static final class Diagram {
		private final Comparison comparison;
		private final Map<Object, ClassView> classesByKey = new IdentityHashMap<>();
		private final Map<EClass, ClassView> classesByObject = new IdentityHashMap<>();
		private final Map<Object, FeatureView> featuresByKey = new IdentityHashMap<>();
		private final Map<EStructuralFeature, FeatureView> featuresByObject = new IdentityHashMap<>();
		private final Set<ClassView> classes = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
		private final Set<FeatureView> features = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
		private final List<InheritanceDiff> inheritanceDiffs = new ArrayList<>();

		private Diagram(Comparison comparison) {
			this.comparison = comparison;
			for (Match match : comparison.getMatches()) {
				registerMatches(match);
			}
			linkFeatureOwners();
			for (Diff diff : comparison.getDifferences()) {
				classify(diff);
			}
			markAffectedOwners();
		}

		private void registerMatches(Match match) {
			EObject current = match.getLeft();
			EObject old = match.getRight();
			if (current instanceof EClass || old instanceof EClass) {
				classFor(match, asClass(current), asClass(old));
			}
			if (current instanceof EStructuralFeature || old instanceof EStructuralFeature) {
				featureFor(match, asFeature(current), asFeature(old));
			}
			for (Match submatch : match.getSubmatches()) {
				registerMatches(submatch);
			}
		}

		private void linkFeatureOwners() {
			for (FeatureView feature : features) {
				if (feature.current != null && feature.current.getEContainingClass() != null) {
					feature.currentOwner = classFor(feature.current.getEContainingClass());
				}
				if (feature.old != null && feature.old.getEContainingClass() != null) {
					feature.oldOwner = classFor(feature.old.getEContainingClass());
				}
			}
		}

		private void classify(Diff diff) {
			if (diff instanceof ReferenceChange change) {
				if (sameFeature(change.getReference(), EcorePackage.Literals.EPACKAGE__ECLASSIFIERS)
						&& change.getValue() instanceof EClass eClass) {
					ClassView view = classFor(eClass);
					view.identityKind = Kind.from(diff.getKind());
					view.affected = true;
					return;
				}
				if (sameFeature(change.getReference(), EcorePackage.Literals.ECLASS__ESTRUCTURAL_FEATURES)
						&& change.getValue() instanceof EStructuralFeature feature) {
					FeatureView view = featureFor(feature);
					view.structuralKind = Kind.from(diff.getKind());
					return;
				}
				if (sameFeature(change.getReference(), EcorePackage.Literals.ECLASS__ESUPER_TYPES)
						&& change.getValue() instanceof EClass superClass) {
					ClassView subclass = classFor(diff.getMatch());
					ClassView superclass = classFor(superClass);
					if (subclass != null && superclass != null) {
						inheritanceDiffs.add(new InheritanceDiff(
							diff, subclass, superclass, Kind.from(diff.getKind())));
						subclass.affected = true;
					}
					return;
				}
			}

			Match match = diff.getMatch();
			if (match == null) {
				return;
			}
			if (match.getLeft() instanceof EStructuralFeature || match.getRight() instanceof EStructuralFeature) {
				FeatureView feature = featureFor(match, asFeature(match.getLeft()), asFeature(match.getRight()));
				if (feature.current != null && feature.old != null) {
					feature.propertyDiffs.add(diff);
				}
			} else if (match.getLeft() instanceof EClass || match.getRight() instanceof EClass) {
				ClassView view = classFor(match);
				if (view != null && view.current != null && view.old != null) {
					view.affected = true;
				}
			}
		}

		private void markAffectedOwners() {
			for (FeatureView feature : features) {
				Kind kind = feature.presentationKind();
				if (kind == Kind.NONE) {
					continue;
				}
				ClassView owner = feature.renderOwner();
				if (owner != null) {
					owner.affected = true;
				}
				if (kind == Kind.MOVE && feature.oldOwner != null) {
					feature.oldOwner.affected = true;
				}
			}
		}

		private ClassView classFor(Match match) {
			if (match == null) {
				return null;
			}
			return classFor(match, asClass(match.getLeft()), asClass(match.getRight()));
		}

		private ClassView classFor(EClass eClass) {
			ClassView existing = classesByObject.get(eClass);
			if (existing != null) {
				return existing;
			}
			Match match = comparison.getMatch(eClass);
			return match == null
				? classFor(eClass, eClass, null)
				: classFor(match, asClass(match.getLeft()), asClass(match.getRight()));
		}

		private ClassView classFor(Object key, EClass current, EClass old) {
			ClassView view = classesByKey.computeIfAbsent(key, ignored -> {
				ClassView created = new ClassView();
				classes.add(created);
				return created;
			});
			if (current != null) {
				view.current = current;
				classesByObject.put(current, view);
			}
			if (old != null) {
				view.old = old;
				classesByObject.put(old, view);
			}
			return view;
		}

		private FeatureView featureFor(EStructuralFeature feature) {
			FeatureView existing = featuresByObject.get(feature);
			if (existing != null) {
				return existing;
			}
			Match match = comparison.getMatch(feature);
			FeatureView view = match == null
				? featureFor(feature, feature, null)
				: featureFor(match, asFeature(match.getLeft()), asFeature(match.getRight()));
			if (view.current != null && view.current.getEContainingClass() != null) {
				view.currentOwner = classFor(view.current.getEContainingClass());
			}
			if (view.old != null && view.old.getEContainingClass() != null) {
				view.oldOwner = classFor(view.old.getEContainingClass());
			}
			return view;
		}

		private FeatureView featureFor(Object key, EStructuralFeature current, EStructuralFeature old) {
			FeatureView view = featuresByKey.computeIfAbsent(key, ignored -> {
				FeatureView created = new FeatureView();
				features.add(created);
				return created;
			});
			if (current != null) {
				view.current = current;
				featuresByObject.put(current, view);
			}
			if (old != null) {
				view.old = old;
				featuresByObject.put(old, view);
			}
			return view;
		}

		private String render() {
			List<FeatureView> associations = features.stream()
				.filter(feature -> feature.displayed() instanceof EReference)
				.filter(feature -> feature.presentationKind() != Kind.NONE)
				.sorted(Comparator.comparing(PlantUmlComparisonExporter::featureSortKey))
				.toList();

			Set<ClassView> visible = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
			classes.stream().filter(view -> view.affected || view.identityKind != Kind.NONE).forEach(visible::add);
			for (FeatureView association : associations) {
				EReference reference = (EReference)association.displayed();
				ClassView owner = association.renderOwner();
				if (owner != null) {
					visible.add(owner);
				}
				if (reference.getEReferenceType() != null) {
					visible.add(classFor(reference.getEReferenceType()));
				}
			}
			for (InheritanceDiff inheritance : inheritanceDiffs) {
				visible.add(inheritance.subclass());
				visible.add(inheritance.superclass());
			}

			List<ClassView> orderedClasses = visible.stream()
				.sorted(Comparator.comparing(PlantUmlComparisonExporter::classSortKey))
				.toList();
			assignAliases(orderedClasses);

			StringBuilder output = new StringBuilder();
			output.append("@startuml\n")
				.append("title Ecore Model Comparison\n\n")
				.append("left to right direction\n")
				.append("hide empty members\n")
				.append("skinparam classAttributeIconSize 0\n\n");

			for (ClassView view : orderedClasses) {
				renderClass(output, view);
			}
			for (FeatureView association : associations) {
				renderAssociation(output, association);
			}
			for (InheritanceDiff inheritance : inheritanceDiffs.stream()
					.sorted(Comparator.comparing(PlantUmlComparisonExporter::inheritanceSortKey)).toList()) {
				renderInheritance(output, inheritance);
			}
			if (!associations.isEmpty() || !inheritanceDiffs.isEmpty()) {
				output.append('\n');
			}
			renderLegend(output);
			output.append("@enduml\n");
			return output.toString();
		}

		private void assignAliases(List<ClassView> ordered) {
			Map<String, Integer> occurrences = new LinkedHashMap<>();
			for (ClassView view : ordered) {
				String base = "C_" + sanitizeIdentifier(qualifiedName(view.displayed()));
				int occurrence = occurrences.merge(base, 1, Integer::sum);
				view.alias = occurrence == 1 ? base : base + "_" + occurrence;
			}
		}

		private void renderClass(StringBuilder output, ClassView view) {
			EClass eClass = view.displayed();
			if (eClass == null) {
				return;
			}
			output.append("class \"").append(escapeQuoted(nameOf(eClass))).append("\" as ")
				.append(view.alias);
			switch (view.presentationKind()) {
				case ADD -> output.append(" <<ADDED>> ").append(ADDED_BACKGROUND);
				case DELETE -> output.append(" <<DELETED>> ").append(DELETED_BACKGROUND);
				case CHANGE -> output.append(" <<CHANGED>> ").append(CHANGED_BACKGROUND);
				case MOVE -> output.append(" <<MOVED>> ").append(CHANGED_BACKGROUND);
				case NONE -> { }
			}
			output.append(" {\n");

			attributesFor(view).stream()
				.sorted(Comparator.comparing(PlantUmlComparisonExporter::featureSortKey))
				.forEach(feature -> output.append("  ").append(renderAttribute(feature)).append('\n'));
			output.append("}\n\n");
		}

		private List<FeatureView> attributesFor(ClassView owner) {
			Set<FeatureView> result = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
			EClass displayed = owner.displayed();
			if (displayed != null) {
				for (EAttribute attribute : displayed.getEAttributes()) {
					result.add(featureFor(attribute));
				}
			}
			features.stream()
				.filter(feature -> feature.displayed() instanceof EAttribute)
				.filter(feature -> feature.renderOwner() == owner)
				.filter(feature -> feature.presentationKind() != Kind.NONE)
				.forEach(result::add);
			return new ArrayList<>(result);
		}

		private String renderAttribute(FeatureView feature) {
			Kind kind = feature.presentationKind();
			EAttribute displayed = (EAttribute)feature.displayed();
			return switch (kind) {
				case ADD -> "<color:" + ADDED_TEXT + "><b>+ " + escapeMarkup(nameOf(displayed))
					+ ":</b> " + escapeMarkup(typeName(displayed)) + "</color>";
				case DELETE -> "<color:" + DELETED_TEXT + "><s><b>- " + escapeMarkup(nameOf(displayed))
					+ ":</b> " + escapeMarkup(typeName(displayed)) + "</s></color>";
				case CHANGE -> renderChangedAttribute(feature);
				case MOVE -> renderMovedAttribute(feature);
				case NONE -> escapeMarkup(declaration(displayed));
			};
		}

		private String renderChangedAttribute(FeatureView feature) {
			String oldName = nameOf(feature.old);
			String currentName = nameOf(feature.current);
			String oldType = typeName(feature.old);
			String currentType = typeName(feature.current);
			StringBuilder text = new StringBuilder("<color:").append(CHANGED_TEXT).append('>');
			if (!Objects.equals(oldName, currentName) || !Objects.equals(oldType, currentType)) {
				text.append("<b>~ ").append(escapeMarkup(oldName)).append(":</b> ")
					.append(escapeMarkup(oldType)).append(" ⇒ <b>")
					.append(escapeMarkup(currentName)).append(":</b> ").append(escapeMarkup(currentType));
			} else {
				text.append("<b>~ ").append(escapeMarkup(currentName)).append(":</b> ")
					.append(escapeMarkup(currentType));
			}
			appendMultiplicityChange(text, feature.old, feature.current);
			appendOtherPropertyChanges(text, feature);
			return text.append("</color>").toString();
		}

		private String renderMovedAttribute(FeatureView feature) {
			StringBuilder text = new StringBuilder("<color:").append(CHANGED_TEXT)
				.append("><b>~ ").append(escapeMarkup(nameOf(feature.current))).append(":</b> ")
				.append(escapeMarkup(typeName(feature.current))).append(" <i>(");
			if (feature.oldOwner == feature.currentOwner) {
				text.append("reordered");
			} else {
				text.append(escapeMarkup(className(feature.oldOwner))).append(" ⇒ ")
					.append(escapeMarkup(className(feature.currentOwner)));
			}
			return text.append(")</i></color>").toString();
		}

		private void renderAssociation(StringBuilder output, FeatureView feature) {
			EReference reference = (EReference)feature.displayed();
			ClassView source = feature.renderOwner();
			ClassView target = reference.getEReferenceType() == null
				? null : classesByObject.get(reference.getEReferenceType());
			if (source == null || target == null || source.alias == null || target.alias == null) {
				return;
			}

			Kind kind = feature.presentationKind();
			output.append(source.alias).append(' ').append(connector(kind)).append(" \"")
				.append(renderMultiplicity(feature)).append("\" ").append(target.alias)
				.append(" : ").append(renderAssociationLabel(feature)).append('\n');
		}

		private String renderMultiplicity(FeatureView feature) {
			EReference displayed = (EReference)feature.displayed();
			if (feature.presentationKind() == Kind.CHANGE
					&& feature.old instanceof EReference oldReference
					&& feature.current instanceof EReference currentReference
					&& !Objects.equals(multiplicity(oldReference), multiplicity(currentReference))) {
				return "<color:" + CHANGED_TEXT + "><s>" + multiplicity(oldReference)
					+ "</s> ⇒ " + multiplicity(currentReference);
			}
			return multiplicity(displayed);
		}

		private String renderAssociationLabel(FeatureView feature) {
			Kind kind = feature.presentationKind();
			EReference displayed = (EReference)feature.displayed();
			String text = switch (kind) {
				case ADD -> "+ " + nameOf(displayed);
				case DELETE -> "- " + nameOf(displayed);
				case CHANGE -> changedReferenceLabel(feature);
				case MOVE -> movedReferenceLabel(feature);
				case NONE -> nameOf(displayed);
			};
			if (kind == Kind.DELETE) {
				return "<color:" + DELETED_TEXT + "><s>" + escapeMarkup(text) + "</s></color>";
			}
			return "<color:" + color(kind) + ">" + escapeMarkup(text) + "</color>";
		}

		private String changedReferenceLabel(FeatureView feature) {
			String oldName = nameOf(feature.old);
			String currentName = nameOf(feature.current);
			String oldType = typeName(feature.old);
			String currentType = typeName(feature.current);
			if (!Objects.equals(oldName, currentName) || !Objects.equals(oldType, currentType)) {
				return "~ " + oldName + ": " + oldType + " ⇒ " + currentName + ": " + currentType;
			}
			return "~ " + currentName;
		}

		private String movedReferenceLabel(FeatureView feature) {
			if (feature.oldOwner == feature.currentOwner) {
				return "~ " + nameOf(feature.current) + " (reordered)";
			}
			return "~ " + nameOf(feature.current) + " (" + className(feature.oldOwner)
				+ " ⇒ " + className(feature.currentOwner) + ")";
		}

		private void renderInheritance(StringBuilder output, InheritanceDiff inheritance) {
			if (inheritance.superclass().alias == null || inheritance.subclass().alias == null) {
				return;
			}
			String connector = switch (inheritance.kind()) {
				case ADD -> "<|-[#16803A]--";
				case DELETE -> "<|-[#B42318,dashed]--";
				case CHANGE, MOVE -> "<|-[#8F5F00]--";
				case NONE -> "<|--";
			};
			String label = marker(inheritance.kind())
				+ (inheritance.kind() == Kind.MOVE ? "supertype moved" : "extends");
			if (inheritance.kind() == Kind.DELETE) {
				label = "<color:" + DELETED_TEXT + "><s>" + label + "</s></color>";
			} else {
				label = "<color:" + color(inheritance.kind()) + ">" + label + "</color>";
			}
			output.append(inheritance.superclass().alias).append(' ').append(connector).append(' ')
				.append(inheritance.subclass().alias).append(" : ").append(label).append('\n');
		}

		private static String connector(Kind kind) {
			return switch (kind) {
				case ADD -> "-[#16803A]->";
				case DELETE -> "-[#B42318,dashed]->";
				case CHANGE, MOVE -> "-[#8F5F00]->";
				case NONE -> "-->";
			};
		}

		private static String color(Kind kind) {
			return switch (kind) {
				case ADD -> ADDED_TEXT;
				case DELETE -> DELETED_TEXT;
				case CHANGE, MOVE -> CHANGED_TEXT;
				case NONE -> "#000000";
			};
		}

		private static String marker(Kind kind) {
			return switch (kind) {
				case ADD -> "+ ";
				case DELETE -> "- ";
				case CHANGE, MOVE -> "~ ";
				case NONE -> "";
			};
		}

		private static void renderLegend(StringBuilder output) {
			output.append("legend right\n")
				.append("  | Color | Difference |\n")
				.append("  |<#D5F5E3><color:#16803A>+|<#D5F5E3> <color:#16803A>Added in the new model |\n")
				.append("  |<#FADBD8><color:#B42318>-|<#FADBD8> <color:#B42318>Deleted from the new model |\n")
				.append("  |<#FCF3CF><color:#8F5F00>~|<#FCF3CF> <color:#8F5F00>Existing element changed |\n")
				.append("  |<#FDF8E2><color:#8F5F00>~|<#FDF8E2> <color:#8F5F00>Moved |\n")
				.append("endlegend\n");
		}

		private void appendOtherPropertyChanges(StringBuilder text, FeatureView feature) {
			Set<String> rendered = new LinkedHashSet<>();
			for (Diff diff : feature.propertyDiffs) {
				EStructuralFeature property = changedProperty(diff);
				if (property == null || isCanonicalProperty(property) || !rendered.add(property.getName())) {
					continue;
				}
				String oldValue = propertyValue(feature.old, property);
				String currentValue = propertyValue(feature.current, property);
				if (!Objects.equals(oldValue, currentValue)) {
					text.append(" {").append(escapeMarkup(property.getName())).append(": <s>")
						.append(escapeMarkup(oldValue)).append("</s> ⇒ ")
						.append(escapeMarkup(currentValue)).append('}');
				}
			}
		}
	}

	private static EStructuralFeature changedProperty(Diff diff) {
		if (diff instanceof AttributeChange change) {
			return change.getAttribute();
		}
		if (diff instanceof ReferenceChange change) {
			return change.getReference();
		}
		return null;
	}

	private static void appendMultiplicityChange(StringBuilder text, ETypedElement old, ETypedElement current) {
		if (old != null && current != null && !Objects.equals(multiplicity(old), multiplicity(current))) {
			text.append(" [<s>").append(multiplicity(old)).append("</s> ⇒ ")
				.append(multiplicity(current)).append(']');
		}
	}

	private static boolean isCanonicalProperty(EStructuralFeature property) {
		return sameFeature(property, EcorePackage.Literals.ENAMED_ELEMENT__NAME)
			|| sameFeature(property, EcorePackage.Literals.ETYPED_ELEMENT__ETYPE)
			|| sameFeature(property, EcorePackage.Literals.ETYPED_ELEMENT__LOWER_BOUND)
			|| sameFeature(property, EcorePackage.Literals.ETYPED_ELEMENT__UPPER_BOUND);
	}

	private static String propertyValue(EObject object, EStructuralFeature property) {
		if (object == null) {
			return "none";
		}
		EStructuralFeature resolved = object.eClass().getEAllStructuralFeatures().stream()
			.filter(candidate -> sameFeature(candidate, property))
			.findFirst().orElse(null);
		return resolved == null ? "none" : valueText(object.eGet(resolved));
	}

	private static String valueText(Object value) {
		if (value == null) {
			return "none";
		}
		if (value instanceof ENamedElement named) {
			return nameOf(named);
		}
		if (value instanceof Collection<?> collection) {
			return collection.stream().map(PlantUmlComparisonExporter::valueText)
				.sorted().collect(Collectors.joining(", "));
		}
		return String.valueOf(value);
	}

	private static EClass asClass(EObject object) {
		return object instanceof EClass eClass ? eClass : null;
	}

	private static EStructuralFeature asFeature(EObject object) {
		return object instanceof EStructuralFeature feature ? feature : null;
	}

	private static String className(ClassView view) {
		return view == null ? "none" : nameOf(view.displayed());
	}

	private static String declaration(ETypedElement element) {
		return nameOf(element) + " : " + typeName(element);
	}

	private static String typeName(ETypedElement element) {
		if (element == null) {
			return "none";
		}
		EClassifier type = element.getEType();
		return type == null ? "none" : nameOf(type);
	}

	private static String multiplicity(ETypedElement element) {
		if (element == null) {
			return "none";
		}
		int lower = element.getLowerBound();
		int upper = element.getUpperBound();
		if (lower == upper) {
			return Integer.toString(lower);
		}
		return lower + ".." + (upper == ETypedElement.UNBOUNDED_MULTIPLICITY ? "*" : upper);
	}

	private static String featureSortKey(FeatureView view) {
		EStructuralFeature feature = view.displayed();
		return classSortKey(view.renderOwner()) + "/" + nameOf(feature) + "/" + feature.eClass().getName();
	}

	private static String inheritanceSortKey(InheritanceDiff diff) {
		return classSortKey(diff.superclass()) + "/" + classSortKey(diff.subclass()) + "/" + diff.kind();
	}

	private static String classSortKey(ClassView view) {
		return view == null ? "" : qualifiedName(view.displayed());
	}

	private static String qualifiedName(EClass eClass) {
		if (eClass == null) {
			return "unnamed";
		}
		List<String> names = new ArrayList<>();
		org.eclipse.emf.ecore.EPackage ePackage = eClass.getEPackage();
		while (ePackage != null) {
			names.add(0, nameOf(ePackage));
			ePackage = ePackage.getESuperPackage();
		}
		names.add(nameOf(eClass));
		return String.join(".", names);
	}

	private static String nameOf(Object object) {
		if (object instanceof ENamedElement named && named.getName() != null && !named.getName().isBlank()) {
			return named.getName();
		}
		return "unnamed";
	}

	private static boolean sameFeature(EStructuralFeature actual, EStructuralFeature expected) {
		if (actual == expected) {
			return true;
		}
		if (actual == null || expected == null || !Objects.equals(actual.getName(), expected.getName())) {
			return false;
		}
		EClass actualOwner = actual.getEContainingClass();
		EClass expectedOwner = expected.getEContainingClass();
		return actualOwner != null && expectedOwner != null
			&& Objects.equals(actualOwner.getName(), expectedOwner.getName())
			&& actualOwner.getEPackage() != null && expectedOwner.getEPackage() != null
			&& Objects.equals(actualOwner.getEPackage().getNsURI(), expectedOwner.getEPackage().getNsURI());
	}

	private static String sanitizeIdentifier(String value) {
		String sanitized = value.replaceAll("[^A-Za-z0-9_]", "_");
		return sanitized.isEmpty() ? "unnamed" : sanitized;
	}

	private static String escapeQuoted(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static String escapeMarkup(String value) {
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static <T> T firstNonNull(T first, T second) {
		return first != null ? first : second;
	}
}
