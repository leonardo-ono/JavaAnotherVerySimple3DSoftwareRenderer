package test;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.util.*;

/**
 * (Very simple 3D software) Renderer class.
 * 
 * @author Leonardo Ono (ono.leo80@gmail.com)
 */
public class Renderer extends BufferedImage {

    private static final double[] LERP = new double[256];
    private static final AffineTransform lerpTransf = new AffineTransform();
    
    static {
        for (int i = 0; i < 256; i++) LERP[i] = i / 255.0;
        try {
            lerpTransf.setTransform(0, 255, 255, 0, 0, 0);
            lerpTransf.invert();
        } catch (NoninvertibleTransformException ex) { }
    }
    
    private final WritableRaster pixelInterceptor = new WritableRaster(
                getSampleModel(), getRaster().getDataBuffer(), new Point()) {

        @Override
        public void setDataElements(int x, int y, Object inData) {
            int[] lerpInfo = (int[]) inData; // how to remove this casting ?
            if (lerpInfo[0] == -8421505) return; // unused half triangle
            double b = LERP[lerpInfo[0] & 0xff];
            double g = LERP[(lerpInfo[0] >> 8) & 0xff];
            double r = LERP[(lerpInfo[0] >> 16) & 0xff];
            double z = (b * uv[i0][0] + g * uv[i1][0] + r * uv[i2][0]);
            int pixelIndex = y * getWidth() + x;
            if (z < depthBuffer[pixelIndex]) {
                double i = 1.0 / z;
                double tx = i * (b * uv[i0][1] + g * uv[i1][1] + r * uv[i2][1]);
                double ty = i * (b * uv[i0][2] + g * uv[i1][2] + r * uv[i2][2]);
                lerpInfo[0] = texture.getRGB((int) tx, (int) ty); 
                if (((lerpInfo[0] >> 24) & 0xff) == 0) return; // transparent px
                super.setDataElements(x, y, lerpInfo);
                depthBuffer[pixelIndex] = z;
            }
        }
    };

    private final BufferedImage lerpTexture, frameBuffer 
            = new BufferedImage(getColorModel(), pixelInterceptor, false, null);

    private final Graphics2D g = frameBuffer.createGraphics();
    private final Graphics2D g2 = createGraphics();
    private final AffineTransform triangleTransf = new AffineTransform();
    private final double[][] sps = new double[4][3], uv = new double[4][3];
    private final double[][] clip = new double[4][], vTmp = new double[2][5];
    private final double[] depthBuffer = new double[getHeight() * getWidth()];
    private final double plane, near;
    private BufferedImage texture;
    private int clipIndex, i0, i1, i2;
    
    public Renderer(int width, int height
            , double fovInRad, double near, BufferedImage lerpTexture) {
        
        super(width, height, BufferedImage.TYPE_INT_ARGB);
        this.lerpTexture = lerpTexture;
        this.plane = (width / 2) / Math.tan(fovInRad / 2);
        this.near = near;
    }

    public void clear(Color backgroundColor) {
        if (backgroundColor != null) {
            g2.setBackground(backgroundColor);
            g2.clearRect(0, 0, getWidth(), getHeight());
        }
        Arrays.fill(depthBuffer, Double.MAX_VALUE);
    }
    
    // ps[0~2][0~4] = { x, y, z, u, v } note: uv in image pixel coordinates
    public void draw(double[][] ps, BufferedImage texture) {
        this.texture = texture;
        clipAgainstNearPlane(ps);
        if (clipIndex == 0) return; // z near plane culling
        for (int i = 0; i < clipIndex; i++) {
            uv[i][0] = 1.0 / clip[i][2];
            uv[i][1] = clip[i][3] * uv[i][0];
            uv[i][2] = clip[i][4] * uv[i][0];
            sps[i][0] = (int) (plane * clip[i][0] * -uv[i][0]) + getWidth() / 2;
            sps[i][1] = (int) (plane * clip[i][1] * uv[i][0]) + getHeight() / 2;
        }
        drawTriangle(0, 1, 2);
        if (clipIndex == 4) drawTriangle(0, 2, 3);
    }
    
    private void clipAgainstNearPlane(double[][] ps) {
        clipIndex = 0;
        int vTmpIndex = 0;
        for (int i = 0; i < 3; i++) {
            double[] pa = ps[i], pb = ps[(i + 1) % 3];
            if (pa[2] <= near) clip[clipIndex++] = pa;
            if ((pa[2] <= near) ^ (pb[2] <= near)) {
                double l = (near - pa[2]) / (pb[2] - pa[2]);
                for (int c = 0; c < 5; c++) {
                    vTmp[vTmpIndex][c] = pa[c] + l * (pb[c] - pa[c]);
                }
                clip[clipIndex++] = vTmp[vTmpIndex++];
            }
        }
    }

    private void drawTriangle(int i0, int i1, int i2) {
        for (int i = 0; i < 2; i++) {
            int s = (i == 0 ? getWidth() : getHeight()) - 1;
            if ((sps[i0][i] < 0 && sps[i1][i] < 0 && sps[i2][i] < 0) 
                || (sps[i0][i] > s && sps[i1][i] > s && sps[i2][i] > s)) return; 
        }
        int c1x = (int) (sps[i0][0] - sps[i2][0]);
        int c1y = (int) (sps[i0][1] - sps[i2][1]);
        int c2x = (int) (sps[i1][0] - sps[i2][0]);
        int c2y = (int) (sps[i1][1] - sps[i2][1]);
        if (c1x * c2y - c1y * c2x > 0) return; // back face culling
        triangleTransf.setTransform(c1x, c1y, c2x, c2y
                            , (int) sps[i2][0], (int) sps[i2][1]);
        
        triangleTransf.concatenate(lerpTransf);
        this.i0 = i0; this.i1 = i1; this.i2 = i2; // set texture uv indices
        g.drawImage(lerpTexture, triangleTransf, null);
    }
    
}
