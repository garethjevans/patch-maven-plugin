package com.garethevans.plugin.patch;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/**
 * Copy resources from maven repository to a folder
 * 
 * @goal create-patch
 * @phase package
 * @execute phase="package"
 * @requiresDependencyResolution runtime
 * @inheritByDefault true
 * @description Create a fuse patch
 */
public class CreatePatchMojo extends AbstractMojo {

	/**
	 * The name of the patch
	 * 
	 * @parameter
	 */
	private String patch;

	/**
	 * The maven resources to copy in the form
	 * mvn:<groupId>/<artifactId>/<version>/<type>/<classifier>
	 * 
	 * @parameter
	 */
	private List<String> resources;

	/**
	 * @parameter expression="${project.build.directory}"
	 */
	private File buildDirectory;

	/**
	 * @parameter default-value="${localRepository}"
	 */
	protected ArtifactRepository localRepo;

	/**
	 * @parameter default-value="${project.remoteArtifactRepositories}"
	 */
	protected List<ArtifactRepository> remoteRepos;

	/**
	 * @component
	 */
	protected ArtifactResolver resolver;

	/**
	 * @component
	 */
	protected ArtifactFactory factory;

	public void execute() throws MojoExecutionException, MojoFailureException {
		getLog().info("Creating patch " + patch + "...");
		try {
			if (resources != null) {
				List<String> resolvedBundles = new ArrayList<String>();
				for (String uri : resources) {
					getLog().info("Resolving " + uri);
					Artifact resourceArtifact = resourceUriToArtifact(uri);
					if (resourceArtifact != null) {
						resolveArtifact(resourceArtifact);
					}
					resolvedBundles.add(toResourceString(resourceArtifact));
				}
				createPatchDescriptor(new File(buildDirectory, patch),
						resolvedBundles);
			}
		} catch (IOException e) {
			throw new MojoExecutionException("Error populating repository", e);
		}
	}

	private String toResourceString(Artifact resourceArtifact) {
		return "mvn:" + resourceArtifact.getGroupId() + "/"
				+ resourceArtifact.getArtifactId() + "/"
				+ resourceArtifact.getVersion() + "/"
				+ resourceArtifact.getType();
	}

	private void resolveArtifact(Artifact resourceArtifact) throws IOException,
			MojoFailureException {
		String dir = resourceArtifact.getGroupId().replace('.', '/') + "/"
				+ resourceArtifact.getArtifactId() + "/"
				+ resourceArtifact.getBaseVersion() + "/";

		String classifier = "";
		if (resourceArtifact.getClassifier() != null) {
			classifier = "-" + resourceArtifact.getClassifier();
		}

		String name = resourceArtifact.getArtifactId() + "-"
				+ resourceArtifact.getBaseVersion() + classifier + "."
				+ resourceArtifact.getType();

		try {
			getLog().info("Copying bundle: " + resourceArtifact);

			resolver.resolve(resourceArtifact, remoteRepos, localRepo);

			File patchDir = new File(buildDirectory, patch);
			File repositoryDir = new File(patchDir, "repository");
			File targetDir = new File(repositoryDir, dir);
			File targetFile = new File(targetDir, name);

			getLog().info("Generating " + targetFile);

			FileUtils.copyInputStreamToFile(new FileInputStream(
					resourceArtifact.getFile()), targetFile);
		} catch (ArtifactResolutionException e) {
			throw new MojoFailureException("Unable to resolve bundle "
					+ resourceArtifact, e);
		} catch (ArtifactNotFoundException e) {
			throw new MojoFailureException("Unable to resolve bundle "
					+ resourceArtifact, e);
		}
	}

	protected Artifact resourceUriToArtifact(String resourceLocation)
			throws MojoExecutionException {
		final int index = resourceLocation.indexOf("mvn:");
		if (index < 0) {
			throw new MojoExecutionException("URL is not a maven URL: "
					+ resourceLocation);
		} else {
			resourceLocation = resourceLocation.substring(index
					+ "mvn:".length());

		}
		String[] elements = resourceLocation.split("/");
		String groupId = elements[0];
		String artifactId = elements[1];
		String version = null;
		String classifier = null;
		String type = "jar";
		if (elements.length > 2) {
			version = elements[2];
			if (elements.length > 3) {
				type = elements[3];
				if (elements.length > 4) {
					classifier = elements[4];
				}
			}
		}
		if (version == null || version.length() == 0) {
			throw new MojoExecutionException("Unable to find version for: "
					+ resourceLocation);
		}
		Artifact artifact = factory.createArtifactWithClassifier(groupId,
				artifactId, version, type, classifier);
		return artifact;
	}

	private void createPatchDescriptor(File directory, List<String> bundles)
			throws IOException {
		File descriptor = new File(directory, patch + ".patch");
		FileUtils.write(descriptor, String.format("id=%s-patch\n", patch), false);
		FileUtils.write(descriptor,
				String.format("bundle.count=%s\n", bundles.size()), true);
		int bundleId = 0;
		for (String bundle : bundles) {
			FileUtils.write(descriptor,
					String.format("bundle.%s=%s\n", bundleId++, bundle), true);
		}
	}

}
