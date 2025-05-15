/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.helloworld.ejemplopostgresql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableModel;

/**
 *
 * @author
 */
public class MainFrame extends javax.swing.JFrame {

    // Valores para la conexión a la base de datos (su nombre, URL, Usuario y Contraseña)
    private static final String DB_NAME = "ArqueologiaDB";
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/" + DB_NAME;
    private static final String DB_USER = "postgres";
    private static final String DB_PWD = "candelaygatu123";

    // Mensajes de error
    private static final String ERROR_MSG_INSERT = "Error al intentar dar de alta a esta persona.";
    private static final String ERROR_MSG_INSERT_INPUT = "No se admiten campos vacíos.";

    // Objetos utilizados para interactuar con la base de datos
    // (conexión, realizar consultas con y sin parámetros, y recibir los resultados)
    private static Connection conn = null;
    private static Statement query = null;
    private static PreparedStatement p_query = null;
    private static ResultSet result = null;

    /**
     * Creates new form MainFrame
     */
    public MainFrame() throws SQLException {
        initComponents();
        label_error.setVisible(false);

        // Una vez creado el formulario e inicializado sus componentes ↑↑↑
        // nos enlazamos con el DBMS para conectarnos a la base de datos solicitada
        // utilizando las credenciales correspondientes
        conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PWD);

        // una vez conectados, nuestro programa creará las tabas que sean necesarias
        // para funcionar, en el caso de que ya no estén creadas
        // (de ahí el "IF NOT EXISTS" luego del "CREATE TABLE").
        // En este ejemplo básico vamos a crear la tabla "ejemplo_personas"
        query = conn.createStatement();
        Statement stmt = conn.createStatement();

        stmt.execute("CREATE TABLE IF NOT EXISTS Sitios ("
                + "S_Cod VARCHAR(20) PRIMARY KEY, "
                + "S_Localidad VARCHAR(100) NOT NULL)");

        stmt.execute("CREATE TABLE IF NOT EXISTS Cuadriculas ("
                + "Cu_Cod VARCHAR(20) PRIMARY KEY, "
                + "S_Cod_Dividido VARCHAR(20), "
                + "FOREIGN KEY (S_Cod_Dividido) REFERENCES Sitios(S_Cod))");

        stmt.execute("CREATE TABLE IF NOT EXISTS Cajas ("
                + "Ca_Cod VARCHAR(20) PRIMARY KEY, "
                + "Ca_Fecha VARCHAR(20), "
                + "Ca_Lugar VARCHAR(100) NOT NULL)");

        stmt.execute("CREATE TABLE IF NOT EXISTS Personas ("
                + "P_Dni INT PRIMARY KEY, "
                + "P_Nombre VARCHAR(50) NOT NULL, "
                + "P_Apellido VARCHAR(50) NOT NULL, "
                + "P_Email VARCHAR(100), "
                + "P_Telefono VARCHAR(20))");

        stmt.execute("CREATE TABLE IF NOT EXISTS Objetos ("
                + "O_Cod VARCHAR(20) PRIMARY KEY, "
                + "O_Nombre VARCHAR(100) NOT NULL, "
                + "O_Tipoextraccion VARCHAR(50), "
                + "O_Alto INT, "
                + "O_Largo INT, "
                + "O_Espesor INT, "
                + "O_Peso INT, "
                + "O_Cantidad INT, "
                + "O_Fecharegistro VARCHAR(20), "
                + "O_Descripcion TEXT, "
                + "O_Origen VARCHAR(100), "
                + "CU_Cod_Asocia VARCHAR(20), "
                + "Ca_Cod_Contiene VARCHAR(20), "
                + "P_Dni_Ingresa INT, "
                + "O_Es CHAR(1) CHECK (O_Es IN ('L', 'C')), "
                + "FOREIGN KEY (CU_Cod_Asocia) REFERENCES Cuadriculas(Cu_Cod), "
                + "FOREIGN KEY (Ca_Cod_Contiene) REFERENCES Cajas(Ca_Cod), "
                + "FOREIGN KEY (P_Dni_Ingresa) REFERENCES Personas(P_Dni))");

        stmt.execute("CREATE TABLE IF NOT EXISTS Liticos ("
                + "O_Cod VARCHAR(20) PRIMARY KEY, "
                + "L_Fechacreacion INT, "
                + "FOREIGN KEY (O_Cod) REFERENCES Objetos(O_Cod))");

        stmt.execute("CREATE TABLE IF NOT EXISTS Ceramicos ("
                + "O_Cod VARCHAR(20) PRIMARY KEY, "
                + "L_Color VARCHAR(30), "
                + "FOREIGN KEY (O_Cod) REFERENCES Objetos(O_Cod))");

        // Inicializamos/Actualizamos la lista de personas del formulario
        // para que muestre las personas que ya están cargadas en el sistema
        updateListaResultados();
    }

    private void updateListaResultados() throws SQLException {
        // realiza la consulta "SELECT * FROM ejemplo_personas" a la base de datos
        // utilizando la conexión ya establecida (almacenada en la variable conn).
        // Finalmente muestra el resultado de la consulta en la tabla principal
        // del programa (jTablaPersonas).
        query = conn.createStatement();

        result = query.executeQuery(
                "SELECT p.P_Dni, p.P_Nombre, p.P_Apellido, COUNT(o.O_Cod) AS Cantidad_Objetos "
                + "FROM Personas p "
                + "LEFT JOIN Objetos o ON p.P_Dni = o.P_Dni_Ingresa "
                + "GROUP BY p.P_Dni, p.P_Nombre, p.P_Apellido "
                + "ORDER BY p.P_Apellido ASC"
        );

        jTablaPersonas.setModel(resultToTable(result));

        result = query.executeQuery(
                "SELECT c.Ca_Cod, c.Ca_Lugar, c.Ca_Fecha, COALESCE(SUM(o.O_Peso), 0) AS Peso_Total "
                + "FROM Cajas c "
                + "LEFT JOIN Objetos o ON c.Ca_Cod = o.Ca_Cod_Contiene "
                + "GROUP BY c.Ca_Cod, c.Ca_Lugar, c.Ca_Fecha"
        );

        jTablaCajas.setModel(resultToTable(result));

        jTabbedPane1.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                int selectedIndex = jTabbedPane1.getSelectedIndex();
                String selectedTitle = jTabbedPane1.getTitleAt(selectedIndex);

                if (selectedTitle.equals("Resumen")) {
                    actualizarResumen();
                }
            }
        });

    }

    private void actualizarResumen() {
        try {
            String sqlPersonas = "SELECT COUNT(*) FROM Personas";
            String sqlCuadriculas = "SELECT COUNT(*) FROM Cuadriculas";
            String sqlObjetos = "SELECT COUNT(*) FROM Objetos";
            String sqlCajas = "SELECT COUNT(*) FROM Cajas";

            Statement stmt = conn.createStatement();

            ResultSet rs = stmt.executeQuery(sqlPersonas);
            if (rs.next()) {
                jCantidadPersonas.setText(String.valueOf(rs.getInt(1)));
            }

            rs = stmt.executeQuery(sqlCuadriculas);
            if (rs.next()) {
                jCantidadCuadriculas.setText(String.valueOf(rs.getInt(1)));
            }

            rs = stmt.executeQuery(sqlObjetos);
            if (rs.next()) {
                jCantidadObjetos.setText(String.valueOf(rs.getInt(1)));
            }

            rs = stmt.executeQuery(sqlCajas);
            if (rs.next()) {
                jCantidadCajas.setText(String.valueOf(rs.getInt(1)));
            }

            stmt.close();
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error al actualizar el resumen.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static DefaultTableModel resultToTable(ResultSet rs) throws SQLException {
        // Esta es una función auxiliar que les permite convertir los resultados de las
        // consultas (ResultSet) a un modelo interpretable para la tabla mostrada en pantalla
        // (es decir, para un objeto de tipo JTable, ver línea 81)
        ResultSetMetaData metaData = rs.getMetaData();

        // creando las culmnas de la tabla
        Vector<String> columnNames = new Vector<String>();
        int columnCount = metaData.getColumnCount();
        for (int column = 1; column <= columnCount; column++) {
            columnNames.add(metaData.getColumnName(column));
        }

        // creando las filas de la tabla con los resultados de la consulta
        Vector<Vector<Object>> data = new Vector<Vector<Object>>();
        while (rs.next()) {
            Vector<Object> vector = new Vector<Object>();
            for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
                vector.add(rs.getObject(columnIndex));
            }
            data.add(vector);
        }

        return new DefaultTableModel(data, columnNames);
    }

    private void updateForm() throws SQLException {
        // actualizar y limpiar el formulario luego de una operación exitosa
        jsDni.setValue(0);
        jtNombre.setText("");
        jtApellido.setText("");
        jtEmail.setText("");
        jtTelefono.setText("");
        jsDniDelete.setValue(0);
        label_error.setVisible(false);
        updateListaResultados();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jTabbedPane1 = new javax.swing.JTabbedPane();
        jPanel1 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTablaPersonas = new javax.swing.JTable();
        jPanel4 = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        jTablaCajas = new javax.swing.JTable();
        jCajasVaciasCheckbox = new javax.swing.JCheckBox();
        jPanel2 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jsDni = new javax.swing.JSpinner();
        jLabel2 = new javax.swing.JLabel();
        jtNombre = new javax.swing.JTextField();
        jbInsertar = new javax.swing.JButton();
        jtApellido = new javax.swing.JTextField();
        jLabel5 = new javax.swing.JLabel();
        jtEmail = new javax.swing.JTextField();
        jLabel6 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        jtTelefono = new javax.swing.JTextField();
        txtAlto = new javax.swing.JTextField();
        jLabel17 = new javax.swing.JLabel();
        jLabel18 = new javax.swing.JLabel();
        jLabel19 = new javax.swing.JLabel();
        jLabel20 = new javax.swing.JLabel();
        jLabel21 = new javax.swing.JLabel();
        jLabel22 = new javax.swing.JLabel();
        jLabel23 = new javax.swing.JLabel();
        jLabel24 = new javax.swing.JLabel();
        jLabel25 = new javax.swing.JLabel();
        jLabel26 = new javax.swing.JLabel();
        jLabel27 = new javax.swing.JLabel();
        jLabel28 = new javax.swing.JLabel();
        jLabel30 = new javax.swing.JLabel();
        txtOCod = new javax.swing.JTextField();
        txtFechaCreacion = new javax.swing.JTextField();
        txtOrigen = new javax.swing.JTextField();
        txtCajaCod = new javax.swing.JTextField();
        txtCuCod = new javax.swing.JTextField();
        txtDniIngresa = new javax.swing.JTextField();
        jLabel29 = new javax.swing.JLabel();
        txtONombre = new javax.swing.JTextField();
        txtCantidad = new javax.swing.JSpinner();
        jScrollPane8 = new javax.swing.JScrollPane();
        txtDescripcion = new javax.swing.JTextArea();
        jLabel31 = new javax.swing.JLabel();
        jbInsertarObjeto = new javax.swing.JButton();
        jLabel32 = new javax.swing.JLabel();
        comboTipo = new javax.swing.JTextField();
        txtTipoExtraccion = new javax.swing.JTextField();
        txtLargo = new javax.swing.JTextField();
        txtPeso = new javax.swing.JTextField();
        txtEspesor = new javax.swing.JTextField();
        jLabel33 = new javax.swing.JLabel();
        txtColor = new javax.swing.JTextField();
        jPanel3 = new javax.swing.JPanel();
        jLabel4 = new javax.swing.JLabel();
        jsDniDelete = new javax.swing.JSpinner();
        jbEliminar = new javax.swing.JButton();
        jbEliminarCaja = new javax.swing.JButton();
        jLabel8 = new javax.swing.JLabel();
        jsCajaDelete = new javax.swing.JTextField();
        jPanel5 = new javax.swing.JPanel();
        jsCajaObjeto = new javax.swing.JTextField();
        jLabel9 = new javax.swing.JLabel();
        jbConsultarObjeto = new javax.swing.JButton();
        jScrollPane3 = new javax.swing.JScrollPane();
        jTablaObjetos = new javax.swing.JTable();
        jFechainicial = new javax.swing.JTextField();
        jLabel10 = new javax.swing.JLabel();
        jLabel11 = new javax.swing.JLabel();
        jFechaFinal = new javax.swing.JTextField();
        jLabel12 = new javax.swing.JLabel();
        jbConsultarObjetofecha = new javax.swing.JButton();
        jTipoCantidad = new javax.swing.JLabel();
        jLabel16 = new javax.swing.JLabel();
        jTodo = new javax.swing.JButton();
        jPanel6 = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        jLabel13 = new javax.swing.JLabel();
        jLabel14 = new javax.swing.JLabel();
        jLabel15 = new javax.swing.JLabel();
        jScrollPane4 = new javax.swing.JScrollPane();
        jCantidadObjetos = new javax.swing.JTextPane();
        jScrollPane5 = new javax.swing.JScrollPane();
        jCantidadCuadriculas = new javax.swing.JTextPane();
        jScrollPane6 = new javax.swing.JScrollPane();
        jCantidadPersonas = new javax.swing.JTextPane();
        jScrollPane7 = new javax.swing.JScrollPane();
        jCantidadCajas = new javax.swing.JTextPane();
        label_error = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Base de Datos (Ejemplo Básico de Conexión e Interacción)");

        jTablaPersonas.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        jScrollPane1.setViewportView(jTablaPersonas);
        jTablaPersonas.getAccessibleContext().setAccessibleName("");
        jTablaPersonas.getAccessibleContext().setAccessibleDescription("");

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 786, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 539, Short.MAX_VALUE)
        );

        jTabbedPane1.addTab("Lista de Personas", jPanel1);

        jTablaCajas.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        jScrollPane2.setViewportView(jTablaCajas);

        jCajasVaciasCheckbox.setText("Mostrar cajas vacías");
        jCajasVaciasCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jCajasVaciasCheckboxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jCajasVaciasCheckbox)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 786, Short.MAX_VALUE)
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addComponent(jCajasVaciasCheckbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 276, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        jTabbedPane1.addTab("Lista de Cajas", jPanel4);

        jLabel1.setText("DNI:");

        jLabel2.setText("Nombre:");

        jbInsertar.setText("Insertar");
        jbInsertar.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jbInsertarActionPerformed(evt);
            }
        });

        jLabel5.setText("Apellido:");

        jLabel6.setText("Email:");

        jLabel7.setText("Teléfono:");

        jLabel17.setText("Extracción");

        jLabel18.setText("Código");

        jLabel19.setText("Alto");

        jLabel20.setText("Largo");

        jLabel21.setText("Espesor");

        jLabel22.setText("Peso");

        jLabel23.setText("Cantidad");

        jLabel24.setText("Fecha ingreso");

        jLabel25.setText("Descripción");

        jLabel26.setText("Origen");

        jLabel27.setText("Clave de cuadrícula");

        jLabel28.setText("Clave de caja");

        jLabel30.setText("DNI del arqueólogo");

        txtOCod.setText("codigo");

        jLabel29.setText("Nombre");

        txtDescripcion.setColumns(20);
        txtDescripcion.setRows(5);
        jScrollPane8.setViewportView(txtDescripcion);

        jLabel31.setText("Insertar Objeto");

        jbInsertarObjeto.setText("Insertar Objeto");
        jbInsertarObjeto.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jbInsertarObjetoActionPerformed(evt);
            }
        });

        jLabel32.setText("Tipo de objeto");

        jLabel33.setText("Color");

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jLabel33)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(txtColor, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel2Layout.createSequentialGroup()
                        .addGap(24, 24, 24)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addComponent(jLabel32)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(comboTipo, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                            .addComponent(jLabel5)
                                            .addComponent(jLabel7)
                                            .addComponent(jLabel2)
                                            .addComponent(jLabel6)
                                            .addComponent(jLabel1))
                                        .addGap(18, 18, 18)
                                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                            .addComponent(jtEmail)
                                            .addComponent(jsDni, javax.swing.GroupLayout.DEFAULT_SIZE, 141, Short.MAX_VALUE)
                                            .addComponent(jtNombre)
                                            .addComponent(jtApellido)
                                            .addComponent(jtTelefono, javax.swing.GroupLayout.Alignment.TRAILING)))
                                    .addComponent(jbInsertar, javax.swing.GroupLayout.Alignment.TRAILING))
                                .addGap(123, 123, 123)
                                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addGroup(jPanel2Layout.createSequentialGroup()
                                        .addComponent(jLabel29)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(txtONombre, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(jPanel2Layout.createSequentialGroup()
                                        .addComponent(jLabel30)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(txtDniIngresa, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(jPanel2Layout.createSequentialGroup()
                                        .addComponent(jLabel28)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(txtCajaCod, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(jPanel2Layout.createSequentialGroup()
                                        .addComponent(jLabel17)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(txtTipoExtraccion, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(jPanel2Layout.createSequentialGroup()
                                        .addComponent(jLabel22)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(txtPeso, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(jPanel2Layout.createSequentialGroup()
                                        .addComponent(jLabel23)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(txtCantidad, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(jPanel2Layout.createSequentialGroup()
                                        .addComponent(jLabel24)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(txtFechaCreacion, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(jPanel2Layout.createSequentialGroup()
                                        .addComponent(jLabel26)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(txtOrigen, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(jPanel2Layout.createSequentialGroup()
                                        .addComponent(jLabel27)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(txtCuCod, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(jLabel20)
                                            .addComponent(jLabel19))
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(txtAlto, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE)
                                            .addComponent(txtLargo, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                    .addGroup(jPanel2Layout.createSequentialGroup()
                                        .addComponent(jLabel25)
                                        .addGap(47, 47, 47)
                                        .addComponent(jScrollPane8, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                                        .addComponent(jLabel18)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(jLabel31)
                                            .addComponent(txtOCod, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                    .addGroup(jPanel2Layout.createSequentialGroup()
                                        .addComponent(jLabel21)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(txtEspesor, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE)))))))
                .addGap(55, 55, 55)
                .addComponent(jbInsertarObjeto)
                .addContainerGap(26, Short.MAX_VALUE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(jsDni, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel31))
                .addGap(18, 18, 18)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel2)
                            .addComponent(jtNombre, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jtApellido, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel5))
                        .addGap(12, 12, 12)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jtEmail, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel6))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel7)
                            .addComponent(jtTelefono, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jbInsertar))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel18)
                            .addComponent(txtOCod, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jbInsertarObjeto))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel29)
                            .addComponent(txtONombre, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel17)
                            .addComponent(txtTipoExtraccion, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel19)
                            .addComponent(txtAlto, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel20)
                            .addComponent(txtLargo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(9, 9, 9)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel21)
                            .addComponent(txtEspesor, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(6, 6, 6)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel22)
                            .addComponent(txtPeso, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel23)
                            .addComponent(txtCantidad, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel24)
                            .addComponent(txtFechaCreacion, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel25)
                            .addComponent(jScrollPane8, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(8, 8, 8)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel26)
                            .addComponent(txtOrigen, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel27, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(txtCuCod, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel28, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(txtCajaCod, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel30, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(txtDniIngresa, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel32, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(comboTipo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel33)
                    .addComponent(txtColor, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(47, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("Insertar", jPanel2);

        jLabel4.setText("DNI:");

        jbEliminar.setText("Eliminar");
        jbEliminar.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jbEliminarActionPerformed(evt);
            }
        });

        jbEliminarCaja.setText("Eliminar");
        jbEliminarCaja.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jbEliminarCajaActionPerformed(evt);
            }
        });

        jLabel8.setText("Caja código:");

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jbEliminar)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addComponent(jLabel4)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jsDniDelete, javax.swing.GroupLayout.PREFERRED_SIZE, 136, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 401, Short.MAX_VALUE)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jbEliminarCaja)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addComponent(jLabel8)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jsCajaDelete, javax.swing.GroupLayout.PREFERRED_SIZE, 136, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGap(13, 13, 13)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel8)
                            .addComponent(jsCajaDelete, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addComponent(jbEliminarCaja))
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel4)
                            .addComponent(jsDniDelete, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addComponent(jbEliminar)))
                .addContainerGap(463, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("Eliminar", jPanel3);

        jLabel9.setText("Caja código:");

        jbConsultarObjeto.setText("Consultar");
        jbConsultarObjeto.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jbConsultarObjetoActionPerformed(evt);
            }
        });

        jTablaObjetos.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        jScrollPane3.setViewportView(jTablaObjetos);

        jLabel10.setText("Fecha inicial:");

        jLabel11.setText("Fecha final:");

        jLabel12.setText("Buscar por fechas");

        jbConsultarObjetofecha.setText("Consultar");
        jbConsultarObjetofecha.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jbConsultarObjetofechaActionPerformed(evt);
            }
        });

        jTipoCantidad.setText("Objetos líticos:x Objectos cerámicos: x");

        jLabel16.setText("Maximo:x - Minimo: x - Promedio: x");

        jTodo.setText("Ver todo");
        jTodo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jTodoActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addGroup(jPanel5Layout.createSequentialGroup()
                                .addComponent(jLabel9)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jsCajaObjeto, javax.swing.GroupLayout.PREFERRED_SIZE, 136, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel5Layout.createSequentialGroup()
                                .addComponent(jLabel10)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jFechainicial)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel5Layout.createSequentialGroup()
                                .addComponent(jbConsultarObjeto)
                                .addGap(18, 18, 18)
                                .addComponent(jLabel12)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(jTodo))
                            .addGroup(jPanel5Layout.createSequentialGroup()
                                .addComponent(jLabel11)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jFechaFinal, javax.swing.GroupLayout.DEFAULT_SIZE, 368, Short.MAX_VALUE)
                                .addGap(27, 27, 27)
                                .addComponent(jbConsultarObjetofecha)))
                        .addGap(18, 18, 18))
                    .addComponent(jScrollPane3)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addComponent(jTipoCantidad)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jLabel16)
                        .addGap(59, 59, 59))))
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel9)
                            .addComponent(jsCajaObjeto, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jbConsultarObjeto)
                            .addComponent(jTodo))
                        .addGap(18, 18, 18))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel5Layout.createSequentialGroup()
                        .addComponent(jLabel12)
                        .addGap(5, 5, 5)))
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jFechainicial, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel10)
                    .addComponent(jFechaFinal, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel11)
                    .addComponent(jbConsultarObjetofecha))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jTipoCantidad)
                    .addComponent(jLabel16))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 194, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(247, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("Objetos", jPanel5);

        jLabel3.setText("Cantidad de personas");

        jLabel13.setText("Cantidad de cuadrículas");

        jLabel14.setText("Cantidad de objetos");

        jLabel15.setText("Cantidad de cajas");

        jCantidadObjetos.setEditable(false);
        jScrollPane4.setViewportView(jCantidadObjetos);

        jCantidadCuadriculas.setEditable(false);
        jScrollPane5.setViewportView(jCantidadCuadriculas);

        jCantidadPersonas.setEditable(false);
        jScrollPane6.setViewportView(jCantidadPersonas);

        jCantidadCajas.setEditable(false);
        jScrollPane7.setViewportView(jCantidadCajas);

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel6Layout.createSequentialGroup()
                        .addComponent(jLabel3)
                        .addGap(116, 116, 116)
                        .addComponent(jScrollPane6, javax.swing.GroupLayout.DEFAULT_SIZE, 316, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel6Layout.createSequentialGroup()
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel14)
                            .addComponent(jLabel15)
                            .addComponent(jLabel13))
                        .addGap(103, 103, 103)
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane5)
                            .addComponent(jScrollPane4)
                            .addComponent(jScrollPane7))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel3)
                    .addComponent(jScrollPane6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(12, 12, 12)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel13))
                .addGap(18, 18, 18)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel14))
                .addGap(18, 18, 18)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane7, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel15))
                .addContainerGap(397, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("Resumen", jPanel6);

        label_error.setForeground(new java.awt.Color(255, 0, 0));
        label_error.setText("mensajes de error");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane1)
            .addGroup(layout.createSequentialGroup()
                .addGap(213, 213, 213)
                .addComponent(label_error, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(265, 265, 265))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(jTabbedPane1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(label_error, javax.swing.GroupLayout.DEFAULT_SIZE, 56, Short.MAX_VALUE))
        );

        jTabbedPane1.getAccessibleContext().setAccessibleName("tab_panel");

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jbEliminarActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jbEliminarActionPerformed

        try {
            int dni = (int) jsDniDelete.getValue();

            // Verificar si el DNI existe en la base de datos
            PreparedStatement checkStmt = conn.prepareStatement("SELECT COUNT(*) FROM Personas WHERE P_Dni = ?");
            checkStmt.setInt(1, dni);
            ResultSet rs = checkStmt.executeQuery();
            rs.next();
            int count = rs.getInt(1);

            if (count == 0) {
                // Si no existe el DNI, mostrar mensaje al usuario
                JOptionPane.showMessageDialog(this, "El DNI " + dni + " no existe en la base de datos.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Confirmar con el usuario antes de eliminar
            int confirm = JOptionPane.showConfirmDialog(this, "¿Está seguro que desea eliminar a la persona con DNI " + dni + "?", "Confirmar eliminación", JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                // Ejecutar la eliminación
                PreparedStatement deleteStmt = conn.prepareStatement("DELETE FROM Personas WHERE P_Dni = ?");
                deleteStmt.setInt(1, dni);
                deleteStmt.executeUpdate();

                // Actualizar el formulario
                updateForm();

                JOptionPane.showMessageDialog(this, "Persona eliminada exitosamente.", "Eliminación completa", JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (Exception ex) {
            Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(this, "Ocurrió un error al intentar eliminar el registro.", "Error", JOptionPane.ERROR_MESSAGE);
        }

    }//GEN-LAST:event_jbEliminarActionPerformed

    private void jbInsertarActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jbInsertarActionPerformed
        // Antes de realizar la inserción, primero controlar que los campos tengan valores
        // válidos. En este caso, por ser un ejemplo, sólo se controla por que el campo "nombre"
        // del usuario no esté vacío, pero para su versión real pueden realizar controles más elaborados.
        // (por ejemplo, que los DNIs y la edad sean números positivos, etc.)
        if (jtNombre.getText().trim().equals("")) {
            label_error.setText(ERROR_MSG_INSERT_INPUT);
            label_error.setVisible(true);
            return;
        }

        try {
            // creamos una consulta INSERT parametrizada por los valores que queremos insertar
            // en este caso, son 3, el DNI, el nombre y la edad, en orden: (?, ?, ?)
            p_query = conn.prepareStatement("INSERT INTO Personas VALUES (?, ?, ? , ?, ?)");

            p_query.setInt(1, (int) jsDni.getValue());
            p_query.setString(2, jtNombre.getText().trim());
            p_query.setString(3, jtApellido.getText().trim());
            p_query.setString(4, jtEmail.getText().trim());// 
            p_query.setString(5, jtTelefono.getText().trim());

            p_query.executeUpdate();

            // finalmente actualizamos nuestra tabla mostrando la lista de personas en el formulario principal
            updateForm();
        } catch (Exception ex) {
            Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE, null, ex);
            label_error.setText(ERROR_MSG_INSERT);
            label_error.setVisible(true);
        }
    }//GEN-LAST:event_jbInsertarActionPerformed


    private void jbEliminarCajaActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jbEliminarCajaActionPerformed
        try {
            String caja = String.valueOf(jsCajaDelete.getText());

            PreparedStatement checkStmt = conn.prepareStatement("SELECT COUNT(*) FROM Cajas WHERE Ca_Cod = ?");
            checkStmt.setString(1, caja);
            ResultSet rs = checkStmt.executeQuery();
            rs.next();
            int count = rs.getInt(1);
            if (count == 0) {
                JOptionPane.showMessageDialog(this, "La caja con código " + caja + " no existe en la base de datos.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            PreparedStatement objStmt = conn.prepareStatement("SELECT COUNT(*) FROM Objetos WHERE Ca_Cod_Contiene = ?");
            objStmt.setString(1, caja);
            ResultSet rsObj = objStmt.executeQuery();
            rsObj.next();
            int objetosCount = rsObj.getInt(1);
            if (objetosCount > 0) {
                JOptionPane.showMessageDialog(this, "No se puede eliminar la caja. Tiene " + objetosCount + " objeto(s) asignado(s).", "Error de integridad", JOptionPane.ERROR_MESSAGE);
                return;
            }

            int confirm = JOptionPane.showConfirmDialog(this, "¿Está seguro que desea eliminar la caja con el código " + caja + "?", "Confirmar eliminación", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                PreparedStatement deleteStmt = conn.prepareStatement("DELETE FROM Cajas WHERE Ca_Cod = ?");
                deleteStmt.setString(1, caja);
                deleteStmt.executeUpdate();
                updateForm();
                JOptionPane.showMessageDialog(this, "Caja eliminada exitosamente.", "Eliminación completa", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE, null, ex);
            JOptionPane.showMessageDialog(this, "Ocurrió un error al intentar eliminar la caja.", "Error", JOptionPane.ERROR_MESSAGE);
        }

    }//GEN-LAST:event_jbEliminarCajaActionPerformed

    private void jbConsultarObjetoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jbConsultarObjetoActionPerformed

        String codigoCaja = jsCajaObjeto.getText().toString().trim(); // o .getText().trim() si es JTextField

        if (codigoCaja.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Por favor, ingrese un código de caja.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String sqlObjetos = "SELECT o.O_Cod, o.O_Nombre, o.O_Tipoextraccion, o.O_Alto, o.O_Largo, o.O_Espesor, "
                + "o.O_Peso, o.O_Cantidad, o.O_Descripcion, "
                + "CASE "
                + "   WHEN l.O_Cod IS NOT NULL THEN 'Lítico' "
                + "   WHEN c.O_Cod IS NOT NULL THEN 'Cerámico' "
                + "   ELSE 'Sin tipo' "
                + "END AS Tipo "
                + "FROM Objetos o "
                + "LEFT JOIN Liticos l ON o.O_Cod = l.O_Cod "
                + "LEFT JOIN Ceramicos c ON o.O_Cod = c.O_Cod "
                + "WHERE o.Ca_Cod_Contiene = ?";

        String sqlConteo = "SELECT "
                + "SUM(CASE WHEN o.O_Es = 'L' THEN 1 ELSE 0 END) AS cantidad_liticos, "
                + "SUM(CASE WHEN o.O_Es = 'C' THEN 1 ELSE 0 END) AS cantidad_ceramicos "
                + "FROM Objetos o "
                + "WHERE o.Ca_Cod_Contiene = ?";

        try {
            // Mostrar objetos
            PreparedStatement ps = conn.prepareStatement(sqlObjetos);
            ps.setString(1, codigoCaja);
            ResultSet rs = ps.executeQuery();

            DefaultTableModel model = new DefaultTableModel(
                    new String[]{"Código", "Nombre", "Tipo Extracción", "Alto", "Largo", "Espesor", "Peso", "Cantidad", "Descripción", "Tipo"}, 0
            );

            boolean encontrado = false;
            while (rs.next()) {
                model.addRow(new Object[]{
                    rs.getString("O_Cod"),
                    rs.getString("O_Nombre"),
                    rs.getString("O_Tipoextraccion"),
                    rs.getInt("O_Alto"),
                    rs.getInt("O_Largo"),
                    rs.getInt("O_Espesor"),
                    rs.getInt("O_Peso"),
                    rs.getInt("O_Cantidad"),
                    rs.getString("O_Descripcion"),
                    rs.getString("Tipo")
                });
                encontrado = true;
            }

            jTablaObjetos.setModel(model);

            try {
                String sql = "SELECT MAX(O_Peso) AS max_peso, MIN(O_Peso) AS min_peso, AVG(O_Peso) AS avg_peso FROM Objetos WHERE O_Peso IS NOT NULL";
                Statement stmt = conn.createStatement();
                rs = stmt.executeQuery(sql);

                if (rs.next()) {
                    int max = rs.getInt("max_peso");
                    int min = rs.getInt("min_peso");
                    double avg = rs.getDouble("avg_peso");

                    jLabel16.setText("Máximo: " + max + " - Mínimo: " + min + " - Promedio: " + String.format("%.2f", avg));
                }

                rs.close();
                stmt.close();
            } catch (SQLException ex) {
                ex.printStackTrace();
                jLabel16.setText("Error al calcular estadísticas.");
            }

            // Mostrar cantidades de tipos
            PreparedStatement psCont = conn.prepareStatement(sqlConteo);
            psCont.setString(1, codigoCaja);
            ResultSet rsCont = psCont.executeQuery();

            if (rsCont.next()) {
                int liticos = rsCont.getInt("cantidad_liticos");
                int ceramicos = rsCont.getInt("cantidad_ceramicos");
                jTipoCantidad.setText("Objetos líticos: " + liticos + "  Objetos cerámicos: " + ceramicos);
            } else {
                jTipoCantidad.setText("Sin datos de cantidad.");
            }

            if (!encontrado) {
                JOptionPane.showMessageDialog(this, "No se encontraron objetos en la caja con código: " + codigoCaja, "Resultado vacío", JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error al consultar objetos o cantidades.", "Error", JOptionPane.ERROR_MESSAGE);
        }

    }//GEN-LAST:event_jbConsultarObjetoActionPerformed

    private void jbConsultarObjetofechaActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jbConsultarObjetofechaActionPerformed

        String fechaInicio = jFechainicial.getText().trim();
        String fechaFin = jFechaFinal.getText().trim();

        try {

            String sql = "SELECT O_Cod, O_Nombre FROM Objetos "
                    + "WHERE TO_DATE(O_Fecharegistro, 'DD-MM-YYYY') BETWEEN TO_DATE(?, 'DD-MM-YYYY') AND TO_DATE(?, 'DD-MM-YYYY')";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, fechaInicio);
            ps.setString(2, fechaFin);
            ResultSet rs = ps.executeQuery();

            DefaultTableModel model = new DefaultTableModel(new String[]{"Código", "Nombre"}, 0);
            while (rs.next()) {
                model.addRow(new Object[]{rs.getString("O_Cod"), rs.getString("O_Nombre")});
            }
            jTablaObjetos.setModel(model);

            sql = "SELECT O_Es, COUNT(*) AS cantidad FROM Objetos "
                    + "WHERE TO_DATE(O_Fecharegistro, 'DD-MM-YYYY') BETWEEN TO_DATE(?, 'DD-MM-YYYY') AND TO_DATE(?, 'DD-MM-YYYY') "
                    + "GROUP BY O_Es";
            ps = conn.prepareStatement(sql);
            ps.setString(1, fechaInicio);
            ps.setString(2, fechaFin);
            rs = ps.executeQuery();

            int liticos = 0, ceramicos = 0;
            while (rs.next()) {
                String tipo = rs.getString("O_Es");
                int cantidad = rs.getInt("cantidad");
                if ("L".equals(tipo)) {
                    liticos = cantidad;
                } else if ("C".equals(tipo)) {
                    ceramicos = cantidad;
                }
            }

            jTipoCantidad.setText("Objetos líticos: " + liticos + "  Objetos cerámicos: " + ceramicos);

            try {
                sql = "SELECT MAX(O_Peso) AS max_peso, MIN(O_Peso) AS min_peso, AVG(O_Peso) AS avg_peso FROM Objetos WHERE O_Peso IS NOT NULL";
                Statement stmt = conn.createStatement();
                rs = stmt.executeQuery(sql);

                if (rs.next()) {
                    int max = rs.getInt("max_peso");
                    int min = rs.getInt("min_peso");
                    double avg = rs.getDouble("avg_peso");

                    jLabel16.setText("Máximo: " + max + " - Mínimo: " + min + " - Promedio: " + String.format("%.2f", avg));
                }

                rs.close();
                stmt.close();
            } catch (SQLException ex) {
                ex.printStackTrace();
                jLabel16.setText("Error al calcular estadísticas.");
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error al consultar objetos por fecha.", "Error", JOptionPane.ERROR_MESSAGE);
        }

    }//GEN-LAST:event_jbConsultarObjetofechaActionPerformed

    private void jCajasVaciasCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jCajasVaciasCheckboxActionPerformed
        // TODO add your handling code here:   

        try {
            Statement query = conn.createStatement();
            ResultSet result;

            if (jCajasVaciasCheckbox.isSelected()) {
                // Mostrar solo cajas vacías
                String sql = "SELECT c.Ca_Cod, c.Ca_Lugar "
                        + "FROM Cajas c "
                        + "LEFT JOIN Objetos o ON c.Ca_Cod = o.Ca_Cod_Contiene "
                        + "WHERE o.O_Cod IS NULL";
                result = query.executeQuery(sql);
            } else {
                // Mostrar todas las cajas con peso total
                String sql = "SELECT c.Ca_Cod, c.Ca_Lugar, COALESCE(SUM(o.O_Peso), 0) AS Peso_Total "
                        + "FROM Cajas c "
                        + "LEFT JOIN Objetos o ON c.Ca_Cod = o.Ca_Cod_Contiene "
                        + "GROUP BY c.Ca_Cod, c.Ca_Lugar";
                result = query.executeQuery(sql);
            }

            jTablaCajas.setModel(resultToTable(result));

        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error al consultar cajas.", "Error", JOptionPane.ERROR_MESSAGE);
        }

    }//GEN-LAST:event_jCajasVaciasCheckboxActionPerformed

    private void jTodoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTodoActionPerformed
        try {
            String sql = "SELECT O_Cod, O_Nombre, O_Tipoextraccion, O_Alto, O_Largo, O_Espesor, "
                    + "O_Peso, O_Cantidad, O_Descripcion, Ca_Cod_Contiene "
                    + "FROM Objetos";

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            jTablaObjetos.setModel(resultToTable(rs));

            //    actualizarEstadisticasPeso();
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error al mostrar todos los objetos.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_jTodoActionPerformed

    private void jbInsertarObjetoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jbInsertarObjetoActionPerformed
        // TODO add your handling code here:
        try {
            String sql = "INSERT INTO Objetos (O_Cod, O_Nombre, O_Tipoextraccion, O_Alto, O_Largo, O_Espesor, "
                    + "O_Peso, O_Cantidad, O_Fecharegistro, O_Descripcion, O_Origen, CU_Cod_Asocia, "
                    + "Ca_Cod_Contiene, P_Dni_Ingresa, O_Es) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            PreparedStatement pst = conn.prepareStatement(sql);
            pst.setString(1, txtOCod.getText().trim());
            pst.setString(2, txtONombre.getText().trim());
            pst.setString(3, txtAlto.getText().trim());
            pst.setInt(4, Integer.parseInt(txtAlto.getText()));
            pst.setInt(5, Integer.parseInt(txtLargo.getText()));
            pst.setInt(6, Integer.parseInt(txtEspesor.getText()));
            pst.setInt(7, Integer.parseInt(txtPeso.getText()));
            pst.setInt(8, (int) txtCantidad.getValue());
            pst.setString(9, txtFechaCreacion.getText().trim());
            pst.setString(10, txtDescripcion.getText().trim());
            pst.setString(11, txtOrigen.getText().trim());
            pst.setString(12, txtCuCod.getText().trim());
            pst.setString(13, txtCajaCod.getText().trim());
            pst.setInt(14, Integer.parseInt(txtDniIngresa.getText()));
            pst.setString(15, comboTipo.getText().trim()); // 'L' o 'C'

            pst.executeUpdate();

            // Insertar en tabla específica (Líticos o Cerámicos)
            String tipo = comboTipo.getText().trim();
            if (tipo.equals("L")) {
                PreparedStatement litico = conn.prepareStatement("INSERT INTO Liticos (O_Cod, L_Fechacreacion) VALUES (?, ?)");
                litico.setString(1, txtOCod.getText().trim());
                litico.setInt(2, Integer.parseInt(txtFechaCreacion.getText()));
                litico.executeUpdate();
            } else if (tipo.equals("C")) {
                PreparedStatement ceramico = conn.prepareStatement("INSERT INTO Ceramicos (O_Cod, L_Color) VALUES (?, ?)");
                ceramico.setString(1, txtOCod.getText().trim());
                ceramico.setString(2, txtColor.getText().trim());
                ceramico.executeUpdate();
            }

            JOptionPane.showMessageDialog(this, "Objeto insertado correctamente.");
            updateForm(); // o updateListaResultados();
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error al insertar el objeto.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_jbInsertarObjetoActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(MainFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(MainFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(MainFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(MainFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                try {
                    new MainFrame().setVisible(true);
                } catch (SQLException ex) {
                    Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField comboTipo;
    private javax.swing.JCheckBox jCajasVaciasCheckbox;
    private javax.swing.JTextPane jCantidadCajas;
    private javax.swing.JTextPane jCantidadCuadriculas;
    private javax.swing.JTextPane jCantidadObjetos;
    private javax.swing.JTextPane jCantidadPersonas;
    private javax.swing.JTextField jFechaFinal;
    private javax.swing.JTextField jFechainicial;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel19;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel20;
    private javax.swing.JLabel jLabel21;
    private javax.swing.JLabel jLabel22;
    private javax.swing.JLabel jLabel23;
    private javax.swing.JLabel jLabel24;
    private javax.swing.JLabel jLabel25;
    private javax.swing.JLabel jLabel26;
    private javax.swing.JLabel jLabel27;
    private javax.swing.JLabel jLabel28;
    private javax.swing.JLabel jLabel29;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel30;
    private javax.swing.JLabel jLabel31;
    private javax.swing.JLabel jLabel32;
    private javax.swing.JLabel jLabel33;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JScrollPane jScrollPane6;
    private javax.swing.JScrollPane jScrollPane7;
    private javax.swing.JScrollPane jScrollPane8;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JTable jTablaCajas;
    private javax.swing.JTable jTablaObjetos;
    private javax.swing.JTable jTablaPersonas;
    private javax.swing.JLabel jTipoCantidad;
    private javax.swing.JButton jTodo;
    private javax.swing.JButton jbConsultarObjeto;
    private javax.swing.JButton jbConsultarObjetofecha;
    private javax.swing.JButton jbEliminar;
    private javax.swing.JButton jbEliminarCaja;
    private javax.swing.JButton jbInsertar;
    private javax.swing.JButton jbInsertarObjeto;
    private javax.swing.JTextField jsCajaDelete;
    private javax.swing.JTextField jsCajaObjeto;
    private javax.swing.JSpinner jsDni;
    private javax.swing.JSpinner jsDniDelete;
    private javax.swing.JTextField jtApellido;
    private javax.swing.JTextField jtEmail;
    private javax.swing.JTextField jtNombre;
    private javax.swing.JTextField jtTelefono;
    private javax.swing.JLabel label_error;
    private javax.swing.JTextField txtAlto;
    private javax.swing.JTextField txtCajaCod;
    private javax.swing.JSpinner txtCantidad;
    private javax.swing.JTextField txtColor;
    private javax.swing.JTextField txtCuCod;
    private javax.swing.JTextArea txtDescripcion;
    private javax.swing.JTextField txtDniIngresa;
    private javax.swing.JTextField txtEspesor;
    private javax.swing.JTextField txtFechaCreacion;
    private javax.swing.JTextField txtLargo;
    private javax.swing.JTextField txtOCod;
    private javax.swing.JTextField txtONombre;
    private javax.swing.JTextField txtOrigen;
    private javax.swing.JTextField txtPeso;
    private javax.swing.JTextField txtTipoExtraccion;
    // End of variables declaration//GEN-END:variables
}
