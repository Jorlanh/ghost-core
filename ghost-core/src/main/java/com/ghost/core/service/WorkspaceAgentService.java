package com.ghost.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.ghost.core.model.WorkspaceJobDocument;
import com.ghost.core.repository.WorkspaceJobRepository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
public class WorkspaceAgentService {

    private static final int MAX_TEXT_CHARS = 120_000;
    private static final int MAX_FILE_BYTES = 6_000_000;
    private static final int MAX_SCAN_FILES = 250;
    private static final Pattern FENCED_FILE = Pattern.compile("```(?:file|path)=([^\\n]+)\\n([\\s\\S]*?)```");
    private static final Pattern SECRET_PATTERN = Pattern.compile("(?i)(api[_-]?key|secret|token|password|passwd|private[_-]?key|client[_-]?secret)\\s*[:=]\\s*['\"]?([^\\s'\"]+)");
    private static final Pattern DANGEROUS_PATTERN = Pattern.compile("(?i)(Runtime\\.getRuntime\\(\\)\\.exec|ProcessBuilder\\(|shell=True|subprocess\\.(?:Popen|run)|child_process\\.(?:exec|execSync)|eval\\(|Function\\(|os\\.system\\()");
    private static final Pattern SQL_RISK_PATTERN = Pattern.compile("(?i)(select\\s+\\*|insert\\s+into|update\\s+.+\\s+set|delete\\s+from|drop\\s+table|where\\s+1\\s*=\\s*1|concat\\(|string_agg\\()");

    private final OllamaClientService ollamaClientService;
    private final WorkspaceJobRepository workspaceJobRepository;
    private final Path workspaceRoot;
    private final Path deliveriesRoot;

    public WorkspaceAgentService(
            OllamaClientService ollamaClientService,
            WorkspaceJobRepository workspaceJobRepository,
            @Value("${ghost.workspace.root:ghost_workspace}") String workspaceRoot,
            @Value("${ghost.workspace.deliveries:ghost_deliveries}") String deliveriesRoot) {
        this.ollamaClientService = ollamaClientService;
        this.workspaceJobRepository = workspaceJobRepository;
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
        this.deliveriesRoot = Path.of(deliveriesRoot).toAbsolutePath().normalize();
    }

    public Map<String, Object> analyzeGithub(String githubUrl, String instruction) {
        String jobId = newJobId();
        Path jobDir = workspaceRoot.resolve(jobId);
        Path repoDir = jobDir.resolve("repo");

        try {
            Files.createDirectories(repoDir);
            validateGithubUrl(githubUrl);

            Process process = new ProcessBuilder("git", "clone", "--depth", "1", githubUrl, repoDir.toString())
                    .redirectErrorStream(true)
                    .start();
            String gitOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exit = process.waitFor();
            if (exit != 0) {
                return error("Falha ao clonar repositorio. Verifique se o link e publico e se o Git esta instalado.", gitOutput);
            }

            return analyzeDirectory(jobId, repoDir, instruction == null ? "Analise este repositorio." : instruction);
        } catch (Exception e) {
            log.warn("GitHub analysis failed: {}", e.getMessage());
            return error("Falha ao analisar GitHub.", e.getMessage());
        }
    }

    public Map<String, Object> analyzeUpload(MultipartFile file, String instruction) {
        String jobId = newJobId();
        Path jobDir = workspaceRoot.resolve(jobId);

        try {
            Files.createDirectories(jobDir);
            String safeName = safeName(file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename());
            Path uploaded = jobDir.resolve(safeName);
            Files.copy(file.getInputStream(), uploaded, StandardCopyOption.REPLACE_EXISTING);

            Path source = jobDir.resolve("source");
            Files.createDirectories(source);
            if (safeName.toLowerCase().endsWith(".zip")) {
                unzip(uploaded, source);
            } else if (safeName.toLowerCase().endsWith(".tar") || safeName.toLowerCase().endsWith(".tgz") || safeName.toLowerCase().endsWith(".tar.gz")) {
                extractTar(uploaded, source);
            } else {
                Files.copy(uploaded, source.resolve(safeName), StandardCopyOption.REPLACE_EXISTING);
            }

            return analyzeDirectory(jobId, source, instruction == null ? "Analise estes arquivos." : instruction);
        } catch (Exception e) {
            log.warn("Upload analysis failed: {}", e.getMessage());
            return error("Falha ao analisar upload.", e.getMessage());
        }
    }

    public Map<String, Object> buildFromPrompt(String instruction, String frontendStack, String backendStack, String databaseStack, String securityMode) {
        String jobId = newJobId();
        Path jobDir = workspaceRoot.resolve(jobId);
        Path source = jobDir.resolve("generated");

        try {
            Files.createDirectories(source);
            String prompt = """
                    Voce e um agente de software local. Crie uma solucao do zero conforme o pedido.
                    Gere arquitetura, estrutura de pastas, arquivos iniciais, README, testes basicos e configuracao de ambiente.
                    Se o pedido envolver frontend, prefira a stack solicitada. Se envolver backend, respeite a linguagem solicitada.
                    Se envolver banco de dados, modele a estrutura para a tecnologia pedida.
                    Responda primeiro com um resumo curto e depois, se gerar arquivos, use blocos exatamente assim:
                    ```file=caminho/relativo.ext
                    conteudo do arquivo
                    ```
                    Evite segredos, chaves reais e comandos perigosos.
                    Pedido:
                    %s

                    Frontend alvo:
                    %s

                    Backend alvo:
                    %s

                    Banco alvo:
                    %s

                    Modo de seguranca:
                    %s
                    """.formatted(
                    instruction,
                    blankIfNull(frontendStack, "react"),
                    blankIfNull(backendStack, "java"),
                    blankIfNull(databaseStack, "postgresql"),
                    blankIfNull(securityMode, "strict")
            );

            String ai = ollamaClientService.chatWithFallback(systemPrompt(), prompt);
            if (ai == null || ai.isBlank()) {
                ai = "Ollama indisponivel. Foi gerado um pacote base com README para continuar o trabalho.\n"
                        + "```file=README.md\n# Projeto GHOST gerado\n\nPedido original:\n\n" + instruction + "\n```";
            }

            int filesCreated = materializeFencedFiles(source, ai);
            if (filesCreated == 0) {
                Files.writeString(source.resolve("README.md"), ai, StandardCharsets.UTF_8);
            }

            return finalizeDelivery(jobId, source, instruction, ai);
        } catch (Exception e) {
            log.warn("Prompt build failed: {}", e.getMessage());
            return error("Falha ao construir projeto.", e.getMessage());
        }
    }

    private Map<String, Object> analyzeDirectory(String jobId, Path source, String instruction) throws IOException {
        String tree = buildTree(source);
        String corpus = collectText(source);
        String prompt = """
                Analise a arvore e o conteudo abaixo. O usuario pode pedir correcao, criacao, explicacao ou plano.
                Entregue uma resposta objetiva, com problemas encontrados, acoes recomendadas e, quando fizer sentido,
                arquivos sugeridos em blocos ```file=caminho```.

                Pedido do usuario:
                %s

                Arvore:
                %s

                Conteudo extraido:
                %s
                """.formatted(instruction, tree, corpus);

        String analysis = ollamaClientService.chatWithFallback(systemPrompt(), prompt);
        if (analysis == null || analysis.isBlank()) {
            analysis = "Ollama indisponivel. A arvore e os arquivos foram processados localmente.\n\n" + tree;
        }

        materializeFencedFiles(source, analysis);
        return finalizeDelivery(jobId, source, instruction, analysis);
    }

    private Map<String, Object> finalizeDelivery(String jobId, Path source, String instruction, String analysis) throws IOException {
        Path delivery = deliveriesRoot.resolve(jobId);
        Files.createDirectories(delivery);

        String tree = buildTree(source);
        Map<String, Object> stackProfile = detectStackProfile(source);
        Map<String, Object> securityScan = securityAudit(source);
        String report = "# Relatorio GHOST Workspace\n\n"
                + "Gerado em: " + LocalDateTime.now() + "\n\n"
                + "## Pedido\n\n" + nullSafe(instruction) + "\n\n"
                + "## Arvore\n\n```text\n" + tree + "\n```\n\n"
                + "## Stack detectada\n\n```json\n" + toJson(stackProfile) + "\n```\n\n"
                + "## Seguranca\n\n```json\n" + toJson(securityScan) + "\n```\n\n"
                + "## Analise\n\n" + nullSafe(analysis) + "\n";

        Files.writeString(delivery.resolve("relatorio.txt"), report, StandardCharsets.UTF_8);
        writeDocx(delivery.resolve("relatorio.docx"), report);
        writePdf(delivery.resolve("relatorio.pdf"), report);
        Files.writeString(delivery.resolve("stack_profile.json"), toJson(stackProfile), StandardCharsets.UTF_8);
        Files.writeString(delivery.resolve("security_audit.json"), toJson(securityScan), StandardCharsets.UTF_8);
        zipDirectory(source, delivery.resolve("codigo.zip"));

        persistWorkspaceJob(jobId, source.getFileName().toString(), instruction, tree, stackProfile, securityScan, analysis, "completed");

        Map<String, Object> links = new LinkedHashMap<>();
        links.put("zip", "/deliveries/" + jobId + "/codigo.zip");
        links.put("txt", "/deliveries/" + jobId + "/relatorio.txt");
        links.put("docx", "/deliveries/" + jobId + "/relatorio.docx");
        links.put("pdf", "/deliveries/" + jobId + "/relatorio.pdf");
        links.put("stack", "/deliveries/" + jobId + "/stack_profile.json");
        links.put("security", "/deliveries/" + jobId + "/security_audit.json");

        return Map.of(
                "status", "SUCCESS",
                "jobId", jobId,
                "tree", tree,
                "analysis", analysis,
                "stackProfile", stackProfile,
                "securityAudit", securityScan,
                "downloads", links
        );
    }

    private int materializeFencedFiles(Path targetRoot, String ai) throws IOException {
        Matcher matcher = FENCED_FILE.matcher(ai);
        int count = 0;
        while (matcher.find()) {
            String relative = matcher.group(1).trim().replace("\\", "/");
            if (relative.startsWith("/") || relative.contains("..")) continue;

            Path file = targetRoot.resolve(relative).normalize();
            if (!file.startsWith(targetRoot)) continue;

            Files.createDirectories(file.getParent());
            Files.writeString(file, matcher.group(2), StandardCharsets.UTF_8);
            count++;
        }
        return count;
    }

    private String buildTree(Path root) throws IOException {
        StringBuilder tree = new StringBuilder(root.getFileName().toString()).append("/\n");
        try (Stream<Path> stream = Files.walk(root, 6)) {
            stream.filter(path -> !path.equals(root))
                    .sorted()
                    .limit(700)
                    .forEach(path -> {
                        Path rel = root.relativize(path);
                        long depth = rel.getNameCount();
                        tree.append("  ".repeat((int) Math.max(0, depth - 1)))
                                .append(Files.isDirectory(path) ? "+ " : "- ")
                                .append(rel.getFileName())
                                .append(Files.isDirectory(path) ? "/" : "")
                                .append("\n");
                    });
        }
        return tree.toString();
    }

    private String collectText(Path root) throws IOException {
        StringBuilder out = new StringBuilder();
        try (Stream<Path> stream = Files.walk(root, 8)) {
            List<Path> files = stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .limit(MAX_SCAN_FILES)
                    .toList();

            for (Path file : files) {
                if (out.length() > MAX_TEXT_CHARS) break;
                String text = extractText(file);
                if (!text.isBlank()) {
                    out.append("\n\n--- FILE: ").append(root.relativize(file)).append(" ---\n")
                            .append(text, 0, Math.min(text.length(), 6000));
                }
            }
        }
        return out.length() > MAX_TEXT_CHARS ? out.substring(0, MAX_TEXT_CHARS) : out.toString();
    }

    private String extractText(Path file) {
        try {
            String name = file.getFileName().toString().toLowerCase();
            long size = Files.size(file);
            if (size > MAX_FILE_BYTES) return "[arquivo grande omitido: " + size + " bytes]";
            if (name.endsWith(".docx")) return extractDocx(file);
            if (name.endsWith(".pdf")) return extractPdfHeuristic(file);
            if (isTextLike(name)) return Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "[falha ao extrair texto: " + e.getMessage() + "]";
        }
        return "";
    }

    private boolean isTextLike(String name) {
        return name.matches(".*\\.(txt|md|java|ts|tsx|js|jsx|json|xml|yaml|yml|html|css|scss|py|ps1|bat|cmd|sql|env|properties|gradle|kt|cs|go|rs|php)$")
                || name.equals("dockerfile")
                || name.equals("readme");
    }

    private String extractDocx(Path file) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    String xml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    return xml.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
                }
            }
        }
        return "";
    }

    private String extractPdfHeuristic(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        String raw = new String(bytes, StandardCharsets.ISO_8859_1);
        return raw.replaceAll("[^\\p{L}\\p{N}\\s.,;:!?@/_-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private Map<String, Object> detectStackProfile(Path root) throws IOException {
        Map<String, Integer> languageHits = new HashMap<>();
        Map<String, Integer> dbHits = new HashMap<>();
        List<String> frameworks = new ArrayList<>();
        List<String> manifests = new ArrayList<>();
        boolean hasFrontend = false;
        boolean hasBackend = false;
        boolean hasAngular = false;
        boolean hasReact = false;
        boolean hasNext = false;
        boolean hasNest = false;
        boolean hasSpring = false;
        boolean hasMongo = false;
        boolean hasSql = false;

        try (Stream<Path> stream = Files.walk(root, 6)) {
            for (Path file : stream.filter(Files::isRegularFile).limit(MAX_SCAN_FILES).toList()) {
                String name = file.getFileName().toString().toLowerCase();
                String text = extractText(file);
                if (!text.isBlank()) {
                    if (name.endsWith(".ts") || name.endsWith(".tsx") || name.endsWith(".js") || name.endsWith(".jsx")) hasFrontend = true;
                    if (name.endsWith(".java") || name.endsWith(".py") || name.endsWith(".cs") || name.endsWith(".go") || name.endsWith(".rs") || name.endsWith(".php") || name.endsWith(".kt")) hasBackend = true;
                }

                if (name.equals("package.json")) {
                    manifests.add("node");
                    if (text.contains("\"react\"")) hasReact = true;
                    if (text.contains("\"next\"")) hasNext = true;
                    if (text.contains("\"@nestjs/")) hasNest = true;
                    if (text.contains("\"angular\"") || text.contains("\"@angular/")) hasAngular = true;
                } else if (name.equals("angular.json")) {
                    manifests.add("angular");
                    hasAngular = true;
                } else if (name.equals("vite.config.ts") || name.equals("vite.config.js") || name.equals("webpack.config.js")) {
                    manifests.add("frontend-build");
                    hasReact = true;
                } else if (name.equals("pom.xml") || name.equals("build.gradle") || name.equals("build.gradle.kts")) {
                    manifests.add("java");
                    if (text.contains("spring-boot")) hasSpring = true;
                } else if (name.endsWith(".sln") || name.endsWith(".csproj")) {
                    manifests.add(".net");
                } else if (name.equals("requirements.txt") || name.equals("pyproject.toml") || name.equals("poetry.lock")) {
                    manifests.add("python");
                } else if (name.equals("composer.json")) {
                    manifests.add("php");
                } else if (name.equals("go.mod")) {
                    manifests.add("go");
                } else if (name.equals("cargo.toml")) {
                    manifests.add("rust");
                } else if (name.endsWith(".sql")) {
                    hasSql = true;
                } else if (name.contains("mongo") || name.endsWith(".json")) {
                    if (text.contains("mongodb") || text.contains("mongoose") || text.contains("mongoClient")) {
                        hasMongo = true;
                    }
                }

                if (name.endsWith(".ts") || name.endsWith(".tsx")) languageHits.merge("typescript", 1, Integer::sum);
                if (name.endsWith(".js") || name.endsWith(".jsx")) languageHits.merge("javascript", 1, Integer::sum);
                if (name.endsWith(".java")) languageHits.merge("java", 1, Integer::sum);
                if (name.endsWith(".py")) languageHits.merge("python", 1, Integer::sum);
                if (name.endsWith(".cs")) languageHits.merge("csharp", 1, Integer::sum);
                if (name.endsWith(".go")) languageHits.merge("go", 1, Integer::sum);
                if (name.endsWith(".rs")) languageHits.merge("rust", 1, Integer::sum);

                if (text.toLowerCase().contains("postgres")) dbHits.merge("postgresql", 1, Integer::sum);
                if (text.toLowerCase().contains("mongodb") || text.toLowerCase().contains("mongoose")) dbHits.merge("mongodb", 1, Integer::sum);
                if (text.toLowerCase().contains("mysql")) dbHits.merge("mysql", 1, Integer::sum);
                if (text.toLowerCase().contains("sqlserver") || text.toLowerCase().contains("mssql")) dbHits.merge("sqlserver", 1, Integer::sum);
                if (text.toLowerCase().contains("sqlite")) dbHits.merge("sqlite", 1, Integer::sum);
            }
        }

        if (hasReact || hasNext || hasAngular || hasNest || hasSpring || hasBackend || hasFrontend) {
            if (hasReact) frameworks.add("React");
            if (hasNext) frameworks.add("Next.js");
            if (hasAngular) frameworks.add("Angular");
            if (hasNest) frameworks.add("NestJS");
            if (hasSpring) frameworks.add("Spring Boot");
        }

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("languages", languageHits);
        profile.put("frameworks", frameworks);
        profile.put("manifests", manifests);
        profile.put("databases", dbHits);
        profile.put("backendLikely", hasBackend);
        profile.put("frontendLikely", hasFrontend);
        profile.put("angularLikely", hasAngular);
        profile.put("reactLikely", hasReact || hasNext);
        List<String> backendStacks = new ArrayList<>();
        if (hasSpring) backendStacks.add("java-spring");
        if (hasNest) backendStacks.add("node-nest");
        if (hasBackend && !hasSpring && !hasNest) backendStacks.add("multi-backend");
        profile.put("backendStacks", backendStacks);

        List<String> databaseHints = new ArrayList<>();
        if (hasMongo) databaseHints.add("mongodb");
        if (hasSql) databaseHints.add("sql");
        profile.put("databaseHints", databaseHints);
        return profile;
    }

    private Map<String, Object> securityAudit(Path root) throws IOException {
        List<Map<String, Object>> findings = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root, 8)) {
            for (Path file : stream.filter(Files::isRegularFile).limit(MAX_SCAN_FILES).toList()) {
                String name = file.getFileName().toString().toLowerCase();
                String content = extractText(file);
                if (content.isBlank()) continue;

                Matcher secrets = SECRET_PATTERN.matcher(content);
                while (secrets.find()) {
                    findings.add(finding("secret", root.relativize(file).toString(), "Possivel segredo ou credencial em texto."));
                }
                if (DANGEROUS_PATTERN.matcher(content).find()) {
                    findings.add(finding("dangerous-execution", root.relativize(file).toString(), "Execucao dinamica ou shell detectada."));
                }
                if (SQL_RISK_PATTERN.matcher(content).find()) {
                    findings.add(finding("sql-risk", root.relativize(file).toString(), "Padrao SQL que merece revisao para injecao/consulta ampla."));
                }
                if (name.equals("package.json") && content.contains("\"scripts\"")) {
                    if (content.contains("postinstall") || content.contains("preinstall")) {
                        findings.add(finding("npm-lifecycle", root.relativize(file).toString(), "Scripts de install podem executar codigo automaticamente."));
                    }
                }
            }
        }

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("findings", findings);
        audit.put("risk", findings.isEmpty() ? "low" : findings.size() < 4 ? "medium" : "high");
        audit.put("recommendations", List.of(
                "Rodar auditoria de dependencias e segredos antes de publicar.",
                "Executar o build em sandbox isolado.",
                "Evitar credenciais no repositorio e usar variaveis de ambiente.",
                "Revisar rotas, comandos de shell e queries SQL dinamicas."
        ));
        return audit;
    }

    private Map<String, Object> finding(String type, String file, String message) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("file", file);
        item.put("message", message);
        return item;
    }

    private String toJson(Object value) {
        if (value instanceof Map<?, ?> map) {
            StringBuilder builder = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) builder.append(",");
                first = false;
                builder.append("\"").append(escapeJson(String.valueOf(entry.getKey()))).append("\":");
                builder.append(toJson(entry.getValue()));
            }
            return builder.append("}").toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder builder = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) builder.append(",");
                builder.append(toJson(list.get(i)));
            }
            return builder.append("]").toString();
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value == null) {
            return "null";
        }
        return "\"" + escapeJson(String.valueOf(value)) + "\"";
    }

    private String escapeJson(String input) {
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void extractTar(Path archive, Path target) throws IOException {
        Process process = new ProcessBuilder("tar", "-xf", archive.toString(), "-C", target.toString())
                .redirectErrorStream(true)
                .start();
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Extraction interrompida", e);
        }
    }

    private void persistWorkspaceJob(
            String jobId,
            String kind,
            String instruction,
            String tree,
            Map<String, Object> stackProfile,
            Map<String, Object> securityScan,
            String analysis,
            String status) {
        if (workspaceJobRepository == null) {
            return;
        }

        try {
            WorkspaceJobDocument document = new WorkspaceJobDocument();
            document.setId(jobId);
            document.setKind(kind);
            document.setSourceLabel(kind);
            document.setInstruction(instruction);
            document.setTree(tree);
            document.setStackProfileJson(toJson(stackProfile));
            document.setSecurityAuditJson(toJson(securityScan));
            document.setAnalysis(analysis);
            document.setStatus(status);
            document.setCreatedAt(java.time.Instant.now());
            workspaceJobRepository.save(document);
        } catch (Exception e) {
            log.warn("Failed to persist workspace job {}: {}", jobId, e.getMessage());
        }
    }

    private void unzip(Path zipFile, Path target) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                Path file = target.resolve(entry.getName()).normalize();
                if (!file.startsWith(target)) continue;
                Files.createDirectories(file.getParent());
                Files.copy(zip, file, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void zipDirectory(Path source, Path zipFile) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipFile));
             Stream<Path> stream = Files.walk(source)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                ZipEntry entry = new ZipEntry(source.relativize(path).toString().replace("\\", "/"));
                zip.putNextEntry(entry);
                Files.copy(path, zip);
                zip.closeEntry();
            }
        }
    }

    private void writeDocx(Path output, String text) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output))) {
            put(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """);
            put(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                    </Relationships>
                    """);
            put(zip, "word/document.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>%s</w:body></w:document>
                    """.formatted(toWordParagraphs(text)));
        }
    }

    private String toWordParagraphs(String text) {
        String[] lines = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").split("\\R");
        StringBuilder body = new StringBuilder();
        for (String line : lines) {
            body.append("<w:p><w:r><w:t xml:space=\"preserve\">").append(line).append("</w:t></w:r></w:p>");
        }
        return body.toString();
    }

    private void writePdf(Path output, String text) throws IOException {
        List<String> lines = wrap(text.replaceAll("[^\\p{ASCII}]", "?"), 92);
        StringBuilder content = new StringBuilder("BT /F1 9 Tf 40 780 Td 12 TL\n");
        for (String line : lines.stream().limit(58).toList()) {
            content.append("(").append(line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")).append(") Tj T*\n");
        }
        content.append("ET");
        byte[] stream = content.toString().getBytes(StandardCharsets.US_ASCII);
        String pdf = "%PDF-1.4\n"
                + "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n"
                + "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n"
                + "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >> endobj\n"
                + "4 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Courier >> endobj\n"
                + "5 0 obj << /Length " + stream.length + " >> stream\n"
                + content + "\nendstream endobj\n"
                + "trailer << /Root 1 0 R >>\n%%EOF";
        Files.writeString(output, pdf, StandardCharsets.US_ASCII);
    }

    private List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\\R")) {
            String value = paragraph;
            while (value.length() > width) {
                lines.add(value.substring(0, width));
                value = value.substring(width);
            }
            lines.add(value);
        }
        return lines;
    }

    private void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private void validateGithubUrl(String url) {
        if (url == null || !url.matches("^https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/?$")) {
            throw new IllegalArgumentException("Use um link publico no formato https://github.com/usuario/repositorio");
        }
    }

    private Map<String, Object> error(String message, String detail) {
        return Map.of("status", "ERROR", "message", message, "detail", detail == null ? "" : detail);
    }

    private String safeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String newJobId() {
        return DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()) + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String blankIfNull(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String systemPrompt() {
        return "Voce e o GHOST Workspace Agent. Ajude somente em projetos autorizados, codigo proprio, auditoria defensiva e criacao de software legitima. Seja direto, tecnico e util.";
    }
}
