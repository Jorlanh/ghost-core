package com.ghost.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

@Service
@Slf4j
public class VisionService {

    /**
     * Tira um print screen de TODOS os monitores conectados simultaneamente.
     * Converte a imagem para Base64 (formato aceito pelo Gemini Vision).
     *
     * @return String em Base64 contendo a imagem da tela, ou null em caso de falha.
     */
    public String captureScreenAsBase64() {
        if (GraphicsEnvironment.isHeadless()) {
            log.error("GHOST >> Sistema operando sem interface gráfica (Headless). Visão indisponível.");
            return null;
        }

        try {
            // 1. Vasculha o ambiente gráfico para achar todos os monitores
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice[] screens = ge.getScreenDevices();
            
            Rectangle allScreenBounds = new Rectangle();
            for (GraphicsDevice screen : screens) {
                Rectangle screenBounds = screen.getDefaultConfiguration().getBounds();
                allScreenBounds = allScreenBounds.union(screenBounds);
            }

            log.info("GHOST >> Ativando nervo óptico. Resolução total capturada: {}x{}", allScreenBounds.width, allScreenBounds.height);

            // 2. Aciona o Robô do Java para tirar a foto física da tela
            Robot robot = new Robot();
            BufferedImage screenCapture = robot.createScreenCapture(allScreenBounds);

            // 3. Comprime a imagem em JPG (para a rede neural processar rápido e não estourar o limite de tokens)
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(screenCapture, "jpg", baos);
            byte[] imageBytes = baos.toByteArray();

            // 4. Codifica em Base64
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            log.info("GHOST >> Print Screen convertido com sucesso. Tamanho do payload: {} KB", (base64Image.length() / 1024));

            return base64Image;

        } catch (Exception e) {
            log.error("GHOST >> Falha crítica no córtex visual: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Retorna os bytes puros da imagem (útil caso queiramos salvar no HD depois)
     */
    public byte[] captureScreenAsBytes() {
        if (GraphicsEnvironment.isHeadless()) return null;

        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice[] screens = ge.getScreenDevices();
            Rectangle allScreenBounds = new Rectangle();
            for (GraphicsDevice screen : screens) {
                allScreenBounds = allScreenBounds.union(screen.getDefaultConfiguration().getBounds());
            }

            Robot robot = new Robot();
            BufferedImage screenCapture = robot.createScreenCapture(allScreenBounds);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(screenCapture, "jpg", baos);
            
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("GHOST >> Erro ao gerar matriz de bytes visuais: {}", e.getMessage());
            return null;
        }
    }
}