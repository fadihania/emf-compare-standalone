package com.github.kolovos.emfcompare.standalone;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;

/** Creates collision-free names for all outputs of one ordered model comparison. */
final class ComparisonOutputNamer {

	private static final String DEFAULT_MODEL_NAME = "model";
	private static final Pattern UNDERSCORE_VERSION = Pattern.compile("^(.+?)_(.+)$");
	private static final Pattern SUFFIX_VERSION = Pattern.compile("^(.+?)(v\\d+|\\d+)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern LABELED_COUNTER =
		Pattern.compile("^(.+)__(?:diff|matchings)__([0-9]+)\\.[^.]+$");
	private static final Pattern COUNTER = Pattern.compile("^(.+)__([0-9]+)\\.[^.]+$");

	private ComparisonOutputNamer() {
	}

	static OutputPaths create(Resource left, Resource right, Path outputDirectory) throws IOException {
		Objects.requireNonNull(left, "left");
		Objects.requireNonNull(right, "right");
		Objects.requireNonNull(outputDirectory, "outputDirectory");

		ModelName leftName = modelName(left.getURI());
		ModelName rightName = modelName(right.getURI());
		String folder = sanitize(folderName(left.getURI(), right.getURI()));
		String model = leftName.base() != null && leftName.base().equals(rightName.base())
			? leftName.base() : DEFAULT_MODEL_NAME;
		String base = String.join("__", sanitize(folder), sanitize(model),
			sanitize(leftName.version()), sanitize(rightName.version()));
		Path modelOutputDirectory = outputDirectory.resolve(folder);
		String fileBase = String.join("__", sanitize(model), sanitize(leftName.version()),
			sanitize(rightName.version()));
		Files.createDirectories(modelOutputDirectory);
		int count = nextCount(modelOutputDirectory, fileBase);
		String numberedBase = fileBase + "__" + count;
		return new OutputPaths(
			modelOutputDirectory.resolve(numberedBase + ".xmi"),
			modelOutputDirectory.resolve(numberedBase + ".puml"),
			modelOutputDirectory.resolve(numberedBase + ".svg"),
			modelOutputDirectory.resolve(fileBase + "__diff__" + count + ".txt"),
			modelOutputDirectory.resolve(fileBase + "__matchings__" + count + ".csv"));
	}

	private static int nextCount(Path outputDirectory, String base) throws IOException {
		int maximum = 0;
		try (Stream<Path> files = Files.list(outputDirectory)) {
			for (Path file : files.filter(Files::isRegularFile).toList()) {
				String filename = file.getFileName().toString();
				Matcher matcher = LABELED_COUNTER.matcher(filename);
				if (!matcher.matches()) {
					matcher = COUNTER.matcher(filename);
				}
				if (matcher.matches() && base.equals(matcher.group(1))) {
					try {
						maximum = Math.max(maximum, Integer.parseInt(matcher.group(2)));
					} catch (NumberFormatException ignored) {
						// Ignore counters outside the supported integer range.
					}
				}
			}
		}
		if (maximum == Integer.MAX_VALUE) {
			throw new IOException("No available output counter for " + base);
		}
		return maximum + 1;
	}

	private static ModelName modelName(URI uri) {
		String stem = stem(uri == null ? null : uri.lastSegment());
		if (stem.matches("(?i)v\\d+") || stem.matches("\\d+")) {
			return new ModelName(null, stem);
		}
		Matcher matcher = UNDERSCORE_VERSION.matcher(stem);
		if (matcher.matches() && looksLikeVersion(matcher.group(2))) {
			return new ModelName(matcher.group(1), matcher.group(2));
		}
		matcher = SUFFIX_VERSION.matcher(stem);
		if (matcher.matches() && !matcher.group(1).isBlank()) {
			return new ModelName(matcher.group(1), matcher.group(2));
		}
		return new ModelName(null, stem.isBlank() ? DEFAULT_MODEL_NAME : stem);
	}

	private static boolean looksLikeVersion(String value) {
		return value.matches("(?i)v?\\d.*") || value.matches("\\d{4}-\\d{2}-\\d{2}.*");
	}

	private static String folderName(URI left, URI right) {
		for (URI uri : new URI[] {left, right}) {
			if (uri != null && uri.segmentCount() > 1) {
				String folder = uri.segment(uri.segmentCount() - 2);
				if (folder != null && !folder.isBlank()) {
					return folder;
				}
			}
		}
		return DEFAULT_MODEL_NAME;
	}

	private static String stem(String filename) {
		if (filename == null || filename.isBlank()) {
			return DEFAULT_MODEL_NAME;
		}
		int extension = filename.lastIndexOf('.');
		return extension > 0 ? filename.substring(0, extension) : filename;
	}

	private static String sanitize(String value) {
		String sanitized = value == null ? DEFAULT_MODEL_NAME
			: value.replaceAll("[^A-Za-z0-9._-]", "_");
		while (sanitized.contains("__")) {
			sanitized = sanitized.replace("__", "_");
		}
		return sanitized.isBlank() ? DEFAULT_MODEL_NAME : sanitized;
	}

	record OutputPaths(Path comparisonModel, Path plantUml, Path svg, Path differences, Path matchingElements) {
	}

	private record ModelName(String base, String version) {
	}
}
