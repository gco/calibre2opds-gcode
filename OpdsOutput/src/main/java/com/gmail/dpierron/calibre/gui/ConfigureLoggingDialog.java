/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * ProfileManagerDialog.java
 *
 * Created on 10 juin 2010, 14:26:23
 */

package com.gmail.dpierron.calibre.gui;

import com.gmail.dpierron.tools.i18n.Localization;


/**
 * @author David Pierron
 */
public class ConfigureLoggingDialog extends javax.swing.JDialog {

  /**
   * Creates new form ProfileManagerDialog
   */
  public ConfigureLoggingDialog(java.awt.Frame parent, boolean modal) {
    super(parent, modal);
    initComponents();
    translateTexts();
  }


  /**
   * Apply localization to this dialog
   */
  private void translateTexts() {
//     cmdButton1.setText(Localization.Main.getText("gui.profile.new"));
//     cmdButton2.setText(Localization.Main.getText("gui.profile.rename"));
    cmdOK.setText((Localization.Main.getText("gui.done")));
    cmdCancel.setText((Localization.Main.getText("gui.cancel")));
  }
  /**
   * This method is called from within the constructor to
   * reset the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        pnlButtons = new javax.swing.JPanel();
        cmdButton1 = new javax.swing.JButton();
        cmdButton2 = new javax.swing.JButton();
        pnlButtonsAtBottom = new javax.swing.JPanel();
        cmdCancel = new javax.swing.JButton();
        cmdOK = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle(Localization.Main.getText("gui.menu.profiles.manage")); // NOI18N
        getContentPane().setLayout(new java.awt.GridBagLayout());

        cmdButton1.setText("cmdButton1");
        pnlButtons.add(cmdButton1);

        cmdButton2.setText("cmdButton2");
        pnlButtons.add(cmdButton2);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        getContentPane().add(pnlButtons, gridBagConstraints);

        cmdCancel.setText("cmdCancel");
        cmdCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmdCancelActionPerformed(evt);
            }
        });
        pnlButtonsAtBottom.add(cmdCancel);

        cmdOK.setText("cmdOK");
        cmdOK.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmdOKActionPerformed(evt);
            }
        });
        pnlButtonsAtBottom.add(cmdOK);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        getContentPane().add(pnlButtonsAtBottom, gridBagConstraints);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void cmdCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmdCancelActionPerformed
       this.setVisible(false);
        // TODO add your handling code here:
    }//GEN-LAST:event_cmdCancelActionPerformed

    private void cmdOKActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmdOKActionPerformed
        this.setVisible(false);
        // TODO add your handling code here:
    }//GEN-LAST:event_cmdOKActionPerformed

  /**
   * @param args the command line arguments
   */
  public static void main(String args[]) {
    java.awt.EventQueue.invokeLater(new Runnable() {
      public void run() {
        ProfileManagerDialog dialog = new ProfileManagerDialog(new javax.swing.JFrame(), true);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
          public void windowClosing(java.awt.event.WindowEvent e) {
            System.exit(0);
          }
        });
        dialog.setVisible(true);
      }
    });
  }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cmdButton1;
    private javax.swing.JButton cmdButton2;
    private javax.swing.JButton cmdCancel;
    private javax.swing.JButton cmdOK;
    private javax.swing.JPanel pnlButtons;
    private javax.swing.JPanel pnlButtonsAtBottom;
    // End of variables declaration//GEN-END:variables

}
