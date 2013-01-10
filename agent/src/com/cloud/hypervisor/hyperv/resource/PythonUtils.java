package com.cloud.hypervisor.hyperv.resource;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;

import org.apache.log4j.Logger;

import com.cloud.serializer.GsonHelper;
import com.google.gson.Gson;

public class PythonUtils {
    public static String s_scriptPathAndName;
    public static String s_pythonExec;
    protected static final Gson s_gson = GsonHelper.getGson();

    private static final Logger s_logger = Logger.getLogger(PythonUtils.class);

    static public <T extends Command, ResultT extends Answer> 
	ResultT callHypervPythonModule(T cmd, Class<ResultT> answerType)
{
String cmdName = cmd.getClass().getSimpleName();
String cmdData = s_gson.toJson(cmd, cmd.getClass());
s_logger.debug("Execute " + cmdName + " using script " + s_scriptPathAndName +
"passing" + cmdData);

List<String> scriptArgs = new ArrayList<String>();
scriptArgs.add(cmdName);

String result = runPythonProcess(
s_scriptPathAndName,
scriptArgs.toArray(new String[0]),
cmdData
);
s_logger.debug("Use Gson to create " +  answerType.getSimpleName() + " from " + result);
ResultT resultAnswer = s_gson.fromJson(result, answerType);
s_logger.info(resultAnswer.toString() + " contains " + 
s_gson.toJson(resultAnswer));
return resultAnswer;
}

public static String runPythonProcess(String pyScript, String[] args,  String jsonData){
String output = "";
// Launch script
try {
List<String> exeArgs = new ArrayList<String>();

exeArgs.add(s_pythonExec);
exeArgs.add(pyScript);

for(String s: args)
{
exeArgs.add(s);
}

// when we launch from the shell, we need to use Cygwin's path to the exe, and 
ProcessBuilder builder = new ProcessBuilder(exeArgs);
builder.redirectErrorStream(true);  // TODO: may want to treat the two separately.
Process proc = builder.start();

// Write data to script's stdin
OutputStream scriptInput = proc.getOutputStream();
OutputStreamWriter siw = new OutputStreamWriter(scriptInput);
BufferedWriter writer = new BufferedWriter(siw);
writer.write(jsonData);
writer.flush();
writer.close();

// Read data to stdout
InputStream is = proc.getInputStream();
InputStreamReader isr = new InputStreamReader(is);
BufferedReader reader = new BufferedReader(isr);

output = reader.readLine();
reader.close();
// TODO:  is waitfor() required?
} catch (Exception ex) {
s_logger.debug("Error calling " + pyScript + 
	"while reading command process stream" + ex.getMessage() );
}
return output;
}

}
