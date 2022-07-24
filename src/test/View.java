package test;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.logging.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * Yet another very simple experimental 3D Software Renderer.
 * 
 * - low resolution perspective correct texture mapping
 * - depth buffer
 * - back face culling
 * - Z near plane clipping and culling
 * - X and Y culling in screen space
 * 
 * @author Leonardo Ono (ono.leo80@gmail.com)
 */
public class View extends Canvas implements Runnable {

    private static class Mesh {

        private double[][] vertices, textureCoordinates;
        private int[][] faces;

        public Mesh(String res) {
            loadModel(res);
        }

        private void loadModel(String res) {
            try (   
                Scanner sc = new Scanner(getClass().getResourceAsStream(res)); 
            ) {
                java.util.List<double[]> verticesTmp = new ArrayList<>();
                java.util.List<double[]> stsTmp = new ArrayList<>();
                java.util.List<int[]> facesTmp = new ArrayList<>();
                sc.useDelimiter("[ /\n]");
                while (sc.hasNext()) {
                    String token = sc.next();
                    if (token.equals("v")) {
                        verticesTmp.add(new double[] { sc.nextDouble()
                                        , sc.nextDouble(), sc.nextDouble() } );
                    }
                    else if (token.equals("vt")) {
                        stsTmp.add(
                            new double[] { sc.nextDouble(), sc.nextDouble() } );
                    }
                    else if (token.equals("f")) {
                        int   v1 = sc.nextInt() - 1, st1 = sc.nextInt() - 1
                            , v2 = sc.nextInt() - 1, st2 = sc.nextInt() - 1
                            , v3 = sc.nextInt() - 1, st3 = sc.nextInt() - 1;

                        facesTmp.add( new int[] { v1, v2, v3, st1, st2, st3 } );
                    }
                }
                vertices = verticesTmp.toArray(new double[0][0]);
                textureCoordinates = stsTmp.toArray(new double[0][0]);
                faces = facesTmp.toArray(new int[0][0]);
            }
        }

        private final double[][] ps = new double[3][5];

        public void draw(Renderer r, double s, double tx, double ty, double tz
                    , double rx, double ry, double rz, BufferedImage texture) {

            int tw = texture.getWidth() - 1;
            int th = texture.getHeight() - 1;
            for (int[] face : faces) {
                for (int i = 0; i < 3; i++) {
                    ps[i][0] = vertices[face[i]][0] * s;
                    ps[i][1] = vertices[face[i]][1] * s;
                    ps[i][2] = vertices[face[i]][2] * s;
                    rotate(rx, 1, 2, ps[i]);
                    rotate(ry, 0, 2, ps[i]);
                    rotate(rz, 0, 1, ps[i]);
                    ps[i][0] += tx;
                    ps[i][1] += ty;
                    ps[i][2] += tz;
                    ps[i][3] = textureCoordinates[face[i + 3]][0] * tw;
                    ps[i][4] = (1.0 - textureCoordinates[face[i + 3]][1]) * th;
                }
                r.draw(ps, texture);
            }        
        }        

        private void rotate(double angle, int i0, int i1, double[] v) {
            double s = Math.sin(angle);
            double c = Math.cos(angle);
            double nx = c * v[i0] - s * v[i1];
            double nz = s * v[i0] + c * v[i1];
            v[i0] = nx;
            v[i1] = nz;
        }

    }
    
    private BufferedImage lerpImage, texture0, texture1, texture2;
    private final Renderer renderer;
    private final Mesh mesh0, mesh1, mesh2;
    private double a0, a1, a2, m1z;
    
    private BufferStrategy bs;
    private boolean running;
    
    public View() {
        try {
            lerpImage = loadTexture("/res/lerp.png");
            texture0 = loadTexture("/res/bricks0.png");
            texture1 = loadTexture("/res/bricks1.png");
            texture2 = loadTexture("/res/bricks2.png");
        } catch (IOException ex) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, null, ex);
        }
        double fov = Math.toRadians(60);
        double zNear = -50.0;
        renderer = new Renderer(400, 300, fov, zNear, lerpImage);
        mesh0 = new Mesh("/res/cube.obj");
        mesh1 = new Mesh("/res/cube.obj");
        mesh2 = new Mesh("/res/cylinder.obj");
    }
    
    private BufferedImage loadTexture(String res) throws IOException {
        InputStream is = getClass().getResourceAsStream(res);
        BufferedImage textureTmp = ImageIO.read(is);
        BufferedImage texture = new BufferedImage(textureTmp.getWidth()
                        , textureTmp.getHeight(), BufferedImage.TYPE_INT_ARGB);
        
        texture.getGraphics().drawImage(textureTmp, 0, 0, null);
        return texture;
    }
    
    
    public void start() {
        createBufferStrategy(2);
        bs = getBufferStrategy();
        new Thread(this).start();
    }

    @Override
    public void run() {
        running = true;
        long previousTime = System.nanoTime();
        long deltaTime = 0;
        long unprocessedTime = 0;
        long timePerFrame = 1000000000 / 60;
        while (running) {
            long currentTime = System.nanoTime();
            deltaTime = currentTime - previousTime;
            previousTime = currentTime;
            unprocessedTime += deltaTime;
            while (unprocessedTime > timePerFrame) {
                unprocessedTime -= timePerFrame;
                update();
            }
            Graphics2D g = (Graphics2D) bs.getDrawGraphics();
            draw((Graphics2D) g);
            g.dispose();
            bs.show();
        }
    }
    
    private void update() {
        a0 += 0.004;
        a1 += 0.008;
        a2 += 0.002;
        m1z = 125 * Math.sin(System.nanoTime() * 0.00000000025);
    }
    
    private void draw(Graphics2D g) {
        renderer.clear(null);
        mesh0.draw(renderer, 10, 20, 0, -100, -a0, a0, -a0, texture0);
        mesh1.draw(renderer, 20, 0, 0, -150 + m1z, a1, -a1, a1, texture1);
        mesh2.draw(renderer, 100, 0, 0, -150, 0, a2, 0, texture2);
        g.drawImage(renderer, 0, 0, getWidth(), getHeight(), this);
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            View view = new View();
            view.setPreferredSize(new Dimension(800, 600));
            JFrame frame = new JFrame();
            frame.setTitle("Yet another very simple 3D Software Renderer");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.getContentPane().add(view);
            frame.pack();
            frame.setVisible(true);
            view.start();
        });
    } 
    
}
