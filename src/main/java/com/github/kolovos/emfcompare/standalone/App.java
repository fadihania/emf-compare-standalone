package com.github.kolovos.emfcompare.standalone;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.Diff;
import org.eclipse.emf.compare.EMFCompare;
import org.eclipse.emf.compare.scope.DefaultComparisonScope;
import org.eclipse.emf.compare.scope.IComparisonScope;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

public class App {

	public static void main(String[] args) throws IOException {
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());

		ResourceSet resourceSet1 = new ResourceSetImpl();
		ResourceSet resourceSet2 = new ResourceSetImpl();

		Resource left = loadClasspathResource(resourceSet1, "/models/v1.ecore", "memory:/v1.ecore");
		Resource right = loadClasspathResource(resourceSet2, "/models/v2.ecore", "memory:/v2.ecore");

		IComparisonScope scope = new DefaultComparisonScope(left, right, null);
		Comparison comparison = EMFCompare.builder().build().compare(scope);

		List<Diff> differences = comparison.getDifferences();

		for (Diff diff : differences) {
			System.out.println(diff);
		}
	}

	private static Resource loadClasspathResource(ResourceSet resourceSet, String classpathLocation, String resourceUri)
		throws IOException {
		URL resourceUrl = App.class.getResource(classpathLocation);
		if (resourceUrl == null) {
			throw new IOException("Missing resource: " + classpathLocation);
		}

		Resource resource = resourceSet.createResource(URI.createURI(resourceUri));
		try (InputStream inputStream = resourceUrl.openStream()) {
			resource.load(inputStream, null);
		}
		return resource;
	}

}
