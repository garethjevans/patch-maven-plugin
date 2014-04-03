package com.garethevans.plugin.patch;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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
	 * @parameter
	 */
	private String patch;

	/**
	 * The maven resources to copy in the form mvn:<groupId>/<artifactId>/<version>/<type>/<classifier>
	 * @parameter
	 */
	private List<String> resources;

	/**
	 * @parameter expression="${project.build.directory}/features-repo"
	 */
	private File repository;

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
			if ( resources != null ) {
				for (String uri : resources) {
					Artifact resourceArtifact = resourceUriToArtifact(uri);
					if (resourceArtifact != null) {
						resolveArtifact(resourceArtifact);
					}
				}
			}
		} catch (IOException e) {
			throw new MojoExecutionException("Error populating repository", e);
		}
	}

	private void resolveArtifact(Artifact resourceArtifact) throws IOException, MojoFailureException {
		String dir =
			resourceArtifact.getGroupId().replace('.', '/') + "/" + resourceArtifact.getArtifactId() + "/" + resourceArtifact.getBaseVersion() + "/";

		String classifier ="";
		if (resourceArtifact.getClassifier() != null) {
			classifier = "-" + resourceArtifact.getClassifier();
		}

		String name =
				resourceArtifact.getArtifactId() + "-" + resourceArtifact.getBaseVersion() + classifier + "." + resourceArtifact.getType();

		try {
			getLog().info("Copying bundle: " + resourceArtifact);
		
	resolver.resolve(resourceArtifact, remoteRepos, localRepo);
			File targetDir = new File(repository, dir);
			FileUtils.copyInputStreamToFile(new FileInputStream(resourceArtifact.getFile()), new File(targetDir, name));
		} catch (ArtifactResolutionException e) {
				throw new MojoFailureException("Unable to resolve bundle " + resourceArtifact, e);
		} catch (ArtifactNotFoundException e) {
				throw new MojoFailureException("Unable to resolve bundle " + resourceArtifact, e);
		}
	}

	protected Artifact resourceUriToArtifact(String resourceLocation) throws MojoExecutionException {
		final int index = resourceLocation.indexOf("mvn:");
		if (index < 0) {
			throw new MojoExecutionException("URL is not a maven URL: " + resourceLocation);
		} else {
			resourceLocation = resourceLocation.substring(index + "mvn:".length());
	
	}
		String[] elements = resourceLocation.split("/");
		String groupId = elements[0];
		String artifactId = elements[1];
		String version = null;
		String classifier = null;
		String type = null;
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
			throw new MojoExecutionException("Unable to find version for: " + resourceLocation);
		}
		Artifact artifact = factory.createArtifactWithClassifier(groupId, artifactId, version, type, classifier);
		return artifact;
	}

}
