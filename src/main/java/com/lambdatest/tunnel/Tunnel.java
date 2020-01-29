package com.lambdatest.tunnel;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Creates and manages a secure tunnel connection to LambdaTest.
 */
public class Tunnel {

    private static final List<String> IGNORE_KEYS = Arrays.asList("user", "key", "binarypath");

    List<String> command;
    private Map<String, String> startOptions;
    private String binaryPath;
    static String line18;
    private int stackCount=0;
    private LineNumberReader lineNumberReader;
    static Queue<String> Q = new LinkedList<String>();

    private TunnelProcess proc = null;

    private final Map<String, String> parameters;

    public Tunnel() {

        parameters = new HashMap<String, String>();
        parameters.put("config", "-config");
        parameters.put("controller", "-controller");
        parameters.put("cui", "-cui");
        parameters.put("customSSHHost", "-customSSHHost");
        parameters.put("customSSHPort", "-customSSHPort");
        parameters.put("customSSHPrivateKey", "-customSSHPrivateKey");
        parameters.put("customSSHUser", "-customSSHUser");
        parameters.put("dir", "-dir");
        parameters.put("dns", "-dns");
        parameters.put("emulateChrome", "-emulateChrome");
        parameters.put("env", "-env");
        parameters.put("infoAPIPort", "-infoAPIPort");
        parameters.put("key", "-key");
        parameters.put("localDomains", "-local-domains");
        parameters.put("logFile", "-logFile");
        parameters.put("mode", "-mode");
        parameters.put("nows", "-nows");
        parameters.put("outputConfig", "-outputConfig");
        parameters.put("pac", "-pac");
        parameters.put("pidfile", "-pidfile");
        parameters.put("port", "-port");
        parameters.put("proxyHost", "-proxy-host");
        parameters.put("proxyPass", "-proxy-pass");
        parameters.put("proxyPort", "-proxy-port");
        parameters.put("proxyUser", "-proxy-user");
        parameters.put("remoteDebug", "-remote-debug");
        parameters.put("server", "-server");
        parameters.put("sharedTunnel", "-shared-tunnel");
        parameters.put("tunnelName", "-tunnelName");
        parameters.put("user", "-user");
        parameters.put("v", "-v");
        parameters.put("version", "-version");

    }

    /**
     * Starts Tunnel instance with options
     *
     * @param options Options for the Tunnel instance
     * @throws Exception
     */
    public void start(Map<String, String> options) throws Exception {
        startOptions = options;
        //Get path of downloaded tunnel in project directory
        TunnelBinary lb = new TunnelBinary();
        binaryPath = lb.getBinaryPath();
        passParametersToTunnel(startOptions, "start");

            proc = runCommand(command);
            BufferedReader stdoutbr = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            BufferedReader stderrbr = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
            String stdout="";
            String stderr="";
            String line;

            clearTheFile();
            while ((line = stdoutbr.readLine()) != null) {

                File newFile = new File("tunnelId.log");

                BufferedWriter writer = new BufferedWriter(
                        new FileWriter(newFile, true)  //Set true for append mode
                );
                writer.newLine();   //Add new line
                writer.write(line);

                writer.close();

                stdout += line;
            }

            while ((line = stderrbr.readLine()) != null) {
                stderr += line;
            }

            /* Finding Tunnel id from file */
            BufferedReader br = new BufferedReader(new FileReader("tunnelId.log"));
            lineNumberReader = new LineNumberReader(br);
            String string;
            int lineCount=0;
            while((string = br.readLine()) != null) {
                lineCount++;
                int posFound = string.indexOf("Tunnel ID");
                if (posFound > - 1) {
                    break;
                }
            }


            line18 = Files.readAllLines(Paths.get("tunnelId.log")).get(lineCount-1);
            line18 = line18.substring(line18.lastIndexOf(":")+1,line18.lastIndexOf('"'));

            //capture tunnelid of running tunnel and store in queue
            Q.add(line18);


    }

    public void stop() throws Exception {
        stackCount = Q.size();

        while(stackCount!=0) {
            stopTunnel();
            stackCount = stackCount-1;
        }
        stackCount=0;
        passParametersToTunnel(startOptions, "stop");
        proc.waitFor();
    }
    public static void clearTheFile() throws IOException {
        FileWriter fwOb = new FileWriter("tunnelId.log", false);
        PrintWriter pwOb = new PrintWriter(fwOb, false);
        pwOb.flush();
        pwOb.close();
        fwOb.close();
    }
    public static void stopTunnel() throws IOException{
        String username = System.getenv("LT_USERNAME");
        String accessKey = System.getenv("LT_ACCESS_KEY");
        String Authentication= username+":"+accessKey;
        String basicsAuthload= "Basic "+ Base64.getEncoder().encodeToString(Authentication.getBytes());
        URL urlForDeleteRequest = new URL("https://api.lambdatest.com/automation/api/v1/tunnels/"+Q.poll());
        HttpURLConnection connection=(HttpURLConnection) urlForDeleteRequest.openConnection();
        connection.setRequestMethod("DELETE");
        connection.setRequestProperty("Authorization", basicsAuthload);
        connection.connect();
        InputStream inputStream = connection.getInputStream();
        int responseCode = connection.getResponseCode();


    }

    // Give parameters to the tunnel for starting it in runCommand.
    private void passParametersToTunnel(Map<String, String> options, String opCode) {
        command = new ArrayList<String>();
        command.add(binaryPath);
        command.add("-user");
        command.add(options.get("user"));
        command.add("-key");
        command.add(options.get("key"));

        for (Map.Entry<String, String> opt : options.entrySet()) {
            String parameter = opt.getKey().trim();
            if (IGNORE_KEYS.contains(parameter)) {
                continue;
            }

                if (parameters.get(parameter) != null) {
                    command.add(parameters.get(parameter));
                } else {
                    command.add("-" + parameter);
                }
                if (opt.getValue() != null) {
                    command.add(opt.getValue().trim());
                }
            }
        }


    protected TunnelProcess runCommand(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        final Process process = processBuilder.start();
        Thread.sleep(5000);
        process.destroy();

        return new TunnelProcess() {
            public InputStream getInputStream() {
                return process.getInputStream();
            }
            public InputStream getErrorStream() {
                return process.getErrorStream();
            }
            public int waitFor() throws Exception {
                return process.waitFor();
            }
        };
    }

    public interface TunnelProcess {
        InputStream getInputStream();

        InputStream getErrorStream();

        int waitFor() throws Exception;
    }
}
