import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.*;
import java.util.stream.Stream;

public class Compile {
    public static void main(String[] args) throws Exception {
        // args[0] = source root, args[1] = output dir
        String srcRoot = args[0];
        String outDir = args[1];
        Files.createDirectories(Paths.get(outDir));
        List<String> sources = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(Paths.get(srcRoot))) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(p -> sources.add(p.toString()));
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            System.err.println("NO_COMPILER_AVAILABLE");
            System.exit(2);
        }
        List<String> options = new ArrayList<>(List.of("-d", outDir, "-Xlint:all"));
        for (int i = 2; i < args.length; i++) options.add(args[i]);
        List<String> allArgs = new ArrayList<>(options);
        allArgs.addAll(sources);
        System.out.println("Compiling " + sources.size() + " files...");
        int result = compiler.run(null, System.out, System.err, allArgs.toArray(new String[0]));
        System.out.println(result == 0 ? "COMPILE_OK" : "COMPILE_FAILED code=" + result);
        System.exit(result);
    }
}
