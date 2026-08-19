package com.github.kolovos.emfcompare.standalone;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.eclipse.emf.compare.Comparison;

/** Writes a linked main diagram and focused diagrams for each affected EClass. */
public final class PlantUmlChangeDiagramExporter {

	public void write(Comparison comparison, Path outputDirectory) throws IOException {
		Objects.requireNonNull(comparison, "comparison");
		Objects.requireNonNull(outputDirectory, "outputDirectory");
		Files.createDirectories(outputDirectory);

		PlantUmlComparisonExporter.ChangeDiagramSet sources =
			new PlantUmlComparisonExporter().exportChangeDiagrams(comparison);
		PlantUmlSvgExporter svgExporter = new PlantUmlSvgExporter();
		Path main = outputDirectory.resolve("main.puml");
		writeAndRender(main, sources.mainSource(), svgExporter);
		for (PlantUmlComparisonExporter.ChangeDiagram diagram : sources.diagrams()) {
			writeAndRender(outputDirectory.resolve(diagram.fileName()), diagram.source(), svgExporter);
		}
	}

	private static void writeAndRender(Path source, String content, PlantUmlSvgExporter svgExporter) throws IOException {
		Files.writeString(source, content, StandardCharsets.UTF_8);
		svgExporter.write(source, svgPath(source));
	}

	private static Path svgPath(Path source) {
		String filename = source.getFileName().toString();
		String stem = filename.endsWith(".puml") ? filename.substring(0, filename.length() - 5) : filename;
		return source.resolveSibling(stem + ".svg");
	}
}
