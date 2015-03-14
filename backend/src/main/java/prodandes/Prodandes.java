/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package prodandes;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;

import javax.ws.rs.POST;
import javax.ws.rs.Path;

import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 *
 * @author Jonathan
 */
@Path("/Servicios")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
/**
 *
 * @author jf.molano1587
 */
public class Prodandes {

    public Connection con;

    @POST
    @Path("/registrarPedido")
    public String registrarPedido(List lista) throws Exception {
        try {
            LinkedHashMap lProducto = (LinkedHashMap) lista.get(0);
            LinkedHashMap lCliente = (LinkedHashMap) lista.get(1);
            LinkedHashMap lCantidad = (LinkedHashMap) lista.get(2);
            LinkedHashMap lFechaEsperada = (LinkedHashMap) lista.get(3);

            String nombreProducto = lProducto.get("nombre").toString();
            int cantidad = (int) lCantidad.get("cantidad");

            int cantidadEnBodega = cantidadProductoEnBodega(nombreProducto);

            abrirConexion();
            String sql = "select max (id) as MAXIMO from PEDIDO_PRODUCTO";
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery(sql);
            int id_pedido = -1;
            if (rs.next()) {
                id_pedido = rs.getInt("MAXIMO");
                
                //Crear pedido nuevo
            }
            cerrarConexion();
            if (id_pedido == -1) {
                throw new Exception("Error asignando ID del pedido");
            }
            if (cantidadEnBodega >= cantidad) {

                reservarProducto(nombreProducto, cantidad, id_pedido);
            }
            else{
                
                //Reservar recursos o pedir suministros
                
            }
            st.close();
            return "";
        } catch (Exception e) {
            return "error";
        }
    }

    @POST
    @Path("/cantidadProductoEnBodega")
    public int cantidadProductoEnBodega(String nombre) throws Exception {
        System.out.println("Entrada parámetro cantidadProductoEnBodega");
        System.out.println(nombre);
        abrirConexion();
        String query = "select count(*) as cuenta from ITEM where NOMBRE_PRODUCTO='" + nombre + "' and ESTADO='Bodega'";
        Statement st = con.createStatement();
        ResultSet rs = st.executeQuery(query);
        int resp = 0;
        if (rs.next()) {
            resp = rs.getInt("cuenta");
        }
        st.close();
        cerrarConexion();
        return resp;
    }

    public void abrirConexion() throws Exception {
        con = null;
        Class.forName("oracle.jdbc.driver.OracleDriver");
        con = DriverManager.getConnection("jdbc:oracle:thin:@157.253.238.224:1531:prod", "ISIS2304271510", "rproxyquark");
    }

    public void cerrarConexion() throws Exception {
        if (con != null) {
            con.close();
            con = null;
        }
    }

    /**
     * Se reserva la cantidad de ese producto que está en bodega, y se pasa a
     * estado reservado y se asocia los items con el pedido
     * @param nombreProducto
     * @param cantidad
     * @param id_pedido
     * @throws Exception 
     */
    public void reservarProducto(String nombreProducto, int cantidad, int id_pedido) throws Exception {

        abrirConexion();
        String query = "select * from ITEM where NOMBRE_PRODUCTO='" + nombreProducto + "' and ESTADO='Bodega'";
        Statement st = con.createStatement();
        ResultSet rs = st.executeQuery(query);

        for (int i = 0; i < cantidad && rs.next(); i++) {

            int id = rs.getInt("ID");
            String sql2 = "update ITEM set ESTADO='Reservado',ID_PEDIDO=" + id_pedido + " where ID = " + id;

            Statement st2 = con.createStatement();
            st2.executeUpdate(sql2);
            st2.close();
        }

        st.close();
        cerrarConexion();
    }

    //-------------------------------------------------------
    // Métodos para ensayar otros métodos
    //-------------------------------------------------------
    
    @POST
    @Path("/reservarProducto")
    public void reservarProductoREST(List lista) throws Exception{

        LinkedHashMap lNombreProducto = (LinkedHashMap) lista.get(0);
        LinkedHashMap lCantidad = (LinkedHashMap) lista.get(1);
        LinkedHashMap lIdPedido = (LinkedHashMap) lista.get(2);
        
        reservarProducto(lNombreProducto.get("nombreProducto").toString()
                , (int)lCantidad.get("cantidad"), 
                (int)lIdPedido.get("id_pedido"));
    }
}
