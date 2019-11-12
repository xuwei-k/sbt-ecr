package sbtecr

import java.util.Base64

import com.amazonaws.regions.Region
import com.amazonaws.services.ecr.{AmazonECR, AmazonECRClientBuilder}
import com.amazonaws.services.ecr.model._
import sbt.Logger

import scala.collection.JavaConverters._

private[sbtecr] object AwsEcr extends Aws {

  def domain(region: Region, accountId: String) = s"${accountId}.dkr.ecr.${region}.${region.getDomain}"

  def createRepository(region: Region,
                       repositoryName: String,
                       repositoryPolicyText: Option[String],
                       repositoryLifecyclePolicyText : Option[String])(implicit logger: Logger): Unit = {
    val client = ecr(region)
    val request = new CreateRepositoryRequest()
    request.setRepositoryName(repositoryName)

    try {
      val result = client.createRepository(request)
      logger.info(s"Repository created in ${region}: arn=${result.getRepository.getRepositoryArn}")
      repositoryPolicyText.foreach(setPolicy(client, repositoryName, _))
      repositoryLifecyclePolicyText.foreach(putLifecyclePolicy(client, repositoryName, _))

    } catch {
      case e: RepositoryAlreadyExistsException =>
        logger.info(s"Repository exists: ${region}/${repositoryName}")
    }
  }

  private def setPolicy(ecr: AmazonECR, repositoryName: String, repositoryPolicyText: String)(implicit logger: Logger): Unit = {
    val request = new SetRepositoryPolicyRequest()
        .withRepositoryName(repositoryName)
        .withPolicyText(repositoryPolicyText)
    ecr.setRepositoryPolicy(request)
    logger.info("Configured policy for ECR repository.")
  }

  private def putLifecyclePolicy(ecr: AmazonECR, repositoryName: String, lifecyclePolicyText: String)(implicit logger: Logger): Unit = {
    val request = new PutLifecyclePolicyRequest()
        .withRepositoryName(repositoryName)
        .withLifecyclePolicyText(lifecyclePolicyText)
    ecr.putLifecyclePolicy(request)
    logger.info("Configured lifecycle policy for ECR repository.")
  }

  def dockerCredentials(region: Region, registryIds: Seq[String])(implicit logger: Logger): (String, String) = {
    val request = new GetAuthorizationTokenRequest().withRegistryIds(registryIds: _*)
    val response = ecr(region).getAuthorizationToken(request)

    response
      .getAuthorizationData
      .asScala
      .map(_.getAuthorizationToken)
      .map(Base64.getDecoder.decode(_))
      .map(new String(_, "UTF-8"))
      .map(_.split(":"))
      .headOption match {
        case Some(creds) if creds.size == 2 =>
          (creds(0), creds(1))
        case _ =>
          throw new IllegalStateException("Authorization token not found.")
      }
  }

  private def ecr(region: Region) = {
    AmazonECRClientBuilder.standard()
                          .withRegion(region.getName())
                          .withCredentials(credentialsProvider())
                          .build()
  }
}
