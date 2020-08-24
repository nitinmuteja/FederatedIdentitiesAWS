import com.amazonaws.auth.*;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.amazonaws.services.securitytoken.model.GetFederationTokenRequest;
import com.amazonaws.services.securitytoken.model.GetFederationTokenResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Base64;

public class Application {


    // get file from classpath, resources folder
    public File getFileFromResources(String fileName) {

        ClassLoader classLoader = getClass().getClassLoader();

        URL resource = classLoader.getResource(fileName);
        if (resource == null) {
            throw new IllegalArgumentException("file is not found!");
        } else {
            return new File(resource.getFile());
        }

    }

    public static void main(String args[])
    {
        try{
            String fileName = "profile.jpg";
            String awsUsername="nitinmuteja";
            String bucketName="bizhivesdev";
            String  encodedFolder= Base64.getEncoder().encodeToString(awsUsername.getBytes());
            Application application =new Application();
            File file= application.getFileFromResources("profile.jpg");


            //Create policy to update content in own directory and read others
            String policy="{'Statement': [{ 'Sid': 'PersonalFolderAccess', 'Action': [ 's3:GetObject','s3:PutObject'],'Effect': 'Allow', 'Resource': 'arn:aws:s3:::[BUCKET-NAME]/[USER-NAME]' },{ 'Sid': 'PersonalBucketAccess', 'Action': [ 's3:GetObject','s3:PutObject'],'Effect': 'Allow', 'Resource': 'arn:aws:s3:::[BUCKET-NAME]/[USER-NAME]/*' }, { 'Sid': 'GeneralBucketList', 'Action': [ 's3:GetObject' ], 'Effect': 'Allow', 'Resource': 'arn:aws:s3:::[BUCKET-NAME]' }]}";
            policy = policy.replace("[BUCKET-NAME]", bucketName);
            policy = policy.replace("[USER-NAME]",  encodedFolder);
            policy = policy.replace("'", "\"");


            //Using admin token i will generate short term  tokens
            //create sts client to get  short  term tokens valid for 3600 seconds
            AWSCredentials awsCredentials= new BasicAWSCredentials("","+9");
            AWSSecurityTokenService sts_client = AWSSecurityTokenServiceClientBuilder.standard()
                    .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
                    .withRegion(Regions.DEFAULT_REGION.getName())
                    .build();

            GetFederationTokenRequest federationtoken= new GetFederationTokenRequest();
            federationtoken.setDurationSeconds(3600);
            federationtoken.setName(awsUsername);
            federationtoken.setPolicy(policy);

            GetFederationTokenResult startSessionResponse = null;
            //send request for sts  token
            startSessionResponse = sts_client.getFederationToken(federationtoken);
            System.out.println(startSessionResponse);
            Credentials credentials=startSessionResponse.getCredentials();

            String accessId=credentials.getAccessKeyId();
            String accessKey=credentials.getSecretAccessKey();
            String sessionToken= credentials.getSessionToken();
            //session credentials for the user
            BasicSessionCredentials sessioncredentials=new BasicSessionCredentials(accessId,accessKey,sessionToken);



            //create amazon s3 client object with the STS tokens
            AmazonS3 s3 = AmazonS3ClientBuilder.standard()
                    .withCredentials(new AWSStaticCredentialsProvider(sessioncredentials))
                    .withRegion(Regions.US_EAST_2)
                    .build();

            //s3.listObjects(bucketName);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType("image/jpeg");



            // create content
            InputStream emptyContent = new FileInputStream(file);
            // create a PutObjectRequest passing the folder name suffixed by /
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName,
                    encodedFolder+"/profile.jpg", emptyContent, metadata);
            // send request to S3 to create folder
            s3.putObject(putObjectRequest);

        }
        catch (Exception ex)
        {
            System.out.println(ex);
        }


    }
}
