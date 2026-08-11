package com.github.kolovos.emfcompare.standalone;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

/** Renders PlantUML source to SVG using the embedded PlantUML Java API. */
public final class PlantUmlSvgExporter {

	public void write(Path plantUmlSource, Path svgOutput) throws IOException {
		Objects.requireNonNull(plantUmlSource, "plantUmlSource");
		Objects.requireNonNull(svgOutput, "svgOutput");
		Path parent = svgOutput.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}

		String source = Files.readString(plantUmlSource, StandardCharsets.UTF_8);
		System.setProperty("java.awt.headless", "true");
		try (OutputStream output = Files.newOutputStream(svgOutput)) {
			new SourceStringReader(source).generateImage(output, new FileFormatOption(FileFormat.SVG));
		}
	}
}
