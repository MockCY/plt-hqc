package com.qinglian.fitness.admin;

import com.qinglian.fitness.auth.WechatGateway;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class DeviceQrCodeService {
    private static final int LABEL_WIDTH = 720;
    private static final int LABEL_HEIGHT = 860;
    private final WechatGateway wechatGateway;
    private final Object miniProgramCodeLock = new Object();
    private volatile byte[] cachedMiniProgramCode;

    public DeviceQrCodeService(WechatGateway wechatGateway) {
        this.wechatGateway = wechatGateway;
    }

    public byte[] generateDeviceLabel(AdminDtos.DeviceRow device) {
        try {
            BufferedImage miniProgramCode = ImageIO.read(new ByteArrayInputStream(miniProgramCodeBytes()));
            if (miniProgramCode == null) {
                throw new IOException("微信小程序码不是有效图片");
            }
            BufferedImage label = new BufferedImage(LABEL_WIDTH, LABEL_HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = label.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(250, 250, 247));
            graphics.fillRect(0, 0, LABEL_WIDTH, LABEL_HEIGHT);
            graphics.setColor(new Color(38, 48, 37));
            drawCentered(graphics, "ARVELLO", new Font(Font.SANS_SERIF, Font.BOLD, 34), 62);
            drawCentered(graphics, "WECHAT MINI PROGRAM", new Font(Font.SANS_SERIF, Font.PLAIN, 18), 92);
            graphics.drawImage(miniProgramCode, 104, 122, 512, 512, null);
            graphics.setColor(new Color(224, 226, 218));
            graphics.fillRoundRect(76, 670, 568, 126, 14, 14);
            graphics.setColor(new Color(80, 88, 77));
            drawCentered(graphics, "SN", new Font(Font.SANS_SERIF, Font.BOLD, 18), 706);
            graphics.setColor(new Color(28, 34, 27));
            drawCentered(graphics, device.serialNumber(), new Font(Font.MONOSPACED, Font.BOLD, 34), 758);
            graphics.dispose();

            var output = new ByteArrayOutputStream();
            ImageIO.write(label, "PNG", output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("生成设备标签失败", exception);
        }
    }

    private byte[] miniProgramCodeBytes() {
        byte[] current = cachedMiniProgramCode;
        if (current != null) {
            return current;
        }
        synchronized (miniProgramCodeLock) {
            current = cachedMiniProgramCode;
            if (current == null) {
                current = wechatGateway.getDeviceBindingMiniProgramCode();
                cachedMiniProgramCode = current;
            }
            return current;
        }
    }

    private void drawCentered(Graphics2D graphics, String text, Font font, int baseline) {
        graphics.setFont(font);
        int x = (LABEL_WIDTH - graphics.getFontMetrics().stringWidth(text)) / 2;
        graphics.drawString(text, x, baseline);
    }
}
