package oscript.compiler;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import oscript.OscriptHost;

public final class JavaPackageReader {
    public static String[] getPackageClasses(String packageName) {
        try {
            String[] classes=getClasses(packageName,true,true);
            return classes;
        } catch (IOException e) {
            OscriptHost.me.error(e + "");
            return new String[0];
        }
    }

    public static String[] getClasses(String packageName, boolean includeInner, boolean recursive) throws IOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        String path = packageName.replace('.', '/');

        Enumeration<URL> resources = cl.getResources(path);
        Set<String> names = new LinkedHashSet<>();

        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            String protocol = url.getProtocol();

            if ("jar".equals(protocol)) {
                // Example: jar:file:/.../lib.jar!/com/foo/bar
                JarURLConnection conn = (JarURLConnection) url.openConnection();
                try (JarFile jar = conn.getJarFile()) {
                    String prefix = path + "/";
                    jar.stream().map(JarEntry::getName).filter(n -> n.endsWith(".class"))
                            .filter(n -> n.startsWith(prefix))
                            .filter(n -> recursive || nextSlashAfter(n, prefix.length()) < 0)
                            .filter(n -> !n.endsWith("module-info.class"))
                            .filter(n -> !n.endsWith("package-info.class"))
                            .filter(n -> includeInner || n.indexOf('$', prefix.length()) < 0).forEach(n -> {
                                String cls = n.substring(0, n.length() - 6).replace('/', '.');
                                names.add(cls);
                            });
                }
            } else if ("file".equals(protocol)) {
                // Convert to Path safely (handles spaces/UTF-8)
                Path _root;
                try {
                    _root = Paths.get(url.toURI());
                } catch (URISyntaxException e) {
                    _root = Paths.get(URLDecoder.decode(url.getPath(), "UTF-8"));
                }
                final Path root = _root;
                int maxDepth = recursive ? Integer.MAX_VALUE : 1;
                try (java.util.stream.Stream<Path> stream = Files.find(root, maxDepth,
                        (p, attrs) -> attrs.isRegularFile() && p.getFileName().toString().endsWith(".class"))) {

                    stream.forEach(p -> {
                        Path rel = root.relativize(p);
                        String relStr = rel.toString().replace(File.separatorChar, '/');
                        if (relStr.endsWith("module-info.class") || relStr.endsWith("package-info.class"))
                            return;
                        if (!includeInner && relStr.indexOf('$') >= 0)
                            return;

                        String simple = relStr.substring(0, relStr.length() - 6); // drop .class
                        String fqcn = packageName + (simple.isEmpty() ? "" : "." + simple.replace('/', '.'));
                        names.add(fqcn);
                    });
                }
            }
        }
        // Stable order (optional)
        List<String> sorted = new ArrayList<>(names);
        Collections.sort(sorted);
        return sorted.toArray(new String[0]);
    }

    private static int nextSlashAfter(String s, int from) {
        int i = s.indexOf('/', from);
        return i;
    }

}
