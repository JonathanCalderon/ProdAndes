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
    @Path("/metodo")
    public String registrarPedido(List lista) throws Exception {
        try{
            LinkedHashMap lProducto = (LinkedHashMap) lista.get(0);
            LinkedHashMap lCliente = (LinkedHashMap) lista.get(1);
            LinkedHashMap lCantidad = (LinkedHashMap) lista.get(2);
            LinkedHashMap lFechaEsperada = (LinkedHashMap) lista.get(3);
            return "";
        }
        catch(Exception e)
        {
            return "error";
        }
    }
    
    @POST
    @Path("/cantidadProductoEnBodega")
    public int cantidadProductoEnBodega(String nombre) throws Exception
    {
        System.out.println("Entrada parámetro cantidadProductoEnBodega");
        System.out.println(nombre);
        abrirConexion();
        String query = "select count(*) as cuenta from ITEM where NOMBRE_PRODUCTO='"+nombre+"' and ESTADO='Bodega'";
        Statement st = con.createStatement();
        ResultSet rs = st.executeQuery(query);
        int resp=0;
        if(rs.next())
        {
            resp = rs.getInt("cuenta");
        }
        return resp;
    }
    
    public void abrirConexion() throws Exception
    {
        con = null;
        Class.forName("oracle.jdbc.driver.OracleDriver");
        con = DriverManager.getConnection("jdbc:oracle:thin:@157.253.238.224:1531:prod", "ISIS2304271510", "rproxyquark");
    }
    
        public void cerrarConexion() throws Exception
    {
        if(con!=null)
        {
        con.close();
        con=null;
        }
    }
}
