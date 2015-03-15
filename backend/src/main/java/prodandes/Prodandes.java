/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package prodandes;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;

import javax.ws.rs.POST;
import javax.ws.rs.Path;

import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

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

    // -------------------------------------------------
    // Requerimientos Funcionales
    // -------------------------------------------------
    @POST
    @Path("/registrarPedido")
    public String registrarPedido(List lista) throws Exception {
        try {

            String resp = "";
            LinkedHashMap lProducto = (LinkedHashMap) lista.get(0);
            LinkedHashMap lCliente = (LinkedHashMap) lista.get(1);
            LinkedHashMap lCantidad = (LinkedHashMap) lista.get(2);
            LinkedHashMap lFechaEsperada = (LinkedHashMap) lista.get(3);
            abrirConexion();
            String sFecha = lFechaEsperada.get("fechaEsperada").toString();

            System.out.println("Fecha: " + sFecha);

            String nombreProducto = lProducto.get("nombre").toString();
            int cantidad = (int) lCantidad.get("cantidad");
            int id_cliente = (int) lCliente.get("id_cliente");

            Calendar c = new GregorianCalendar();
            String fechaSolicitud = c.get(GregorianCalendar.DAY_OF_MONTH) + "-"
                    + c.get(GregorianCalendar.MONTH) + "-" + c.get(GregorianCalendar.YEAR);

            System.out.println("FEcha actual " + fechaSolicitud);
            String sql = "select max (id) as MAXIMO from PEDIDO_PRODUCTO";
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery(sql);
            int id_pedido = -1;
            if (rs.next()) {
                id_pedido = rs.getInt("MAXIMO") + 1;

                //Crear pedido nuevo
                sql = "INSERT INTO PEDIDO_PRODUCTO (id,FECHA_ESPERADA_ENTREGA,Estado,cantidad_producto"
                        + ",id_cliente,fecha_solicitud) VALUES (" + id_pedido + ",TO_DATE"
                        + "('" + sFecha + "','DD-MM-YYYY'),'Espera'," + cantidad + ","
                        + id_cliente + " ,TO_DATE('" + fechaSolicitud + "','DD-MM-YYYY'))";

                Statement st2 = con.createStatement();

                st2.executeUpdate(sql);

                st2.close();
            }
            st.close();

            if (id_pedido == -1) {
                throw new Exception("Error asignando ID del pedido");
            }
            int productosReservados = reservarProductoBodega(nombreProducto, cantidad, id_pedido);

            if (productosReservados == cantidad) {

                //Modificar fecha entrega
                Statement st3 = con.createStatement();
                sql = "update PEDIDO_PRODUCTO set FECHA_ENTREGA=TO_DATE('" + sFecha + "','DD-MM-YYYY'),"
                        + "ESTADO='En Bodega'"
                        + "where id=" + id_pedido;
                st3.executeUpdate(sql);
                st3.close();
                resp = "Se ha registrado su pedido, la fecha de entrega es " + sFecha;
            } else {

                // Verificar que la cantidad disminuye dependiendo de cuantos productos ya están en bodega
                cantidad = cantidad - productosReservados;

                //Reservar recursos(materias primas) o pedir suministros
                int numProductosPotencial = Integer.MAX_VALUE;

                //Averiguar Componentes en bodega
                sql = "select * from COMPONENTES_PRODUCTO WHERE id_producto=" + nombreProducto;
                Statement st3 = con.createStatement();
                rs = st3.executeQuery(sql);

                while (rs.next()) {

                    String id_componente = rs.getString("id_componente");
                    int cantidad_unidades = rs.getInt("cantidad_unidades");

                    int numComponentes = cantidadComponentesBodega(id_componente);
                    if (numComponentes >= cantidad_unidades) {

                        int alcanzanComponentes = numComponentes / cantidad_unidades;
                        numProductosPotencial = Math.min(alcanzanComponentes, numProductosPotencial);
                    }
                }

                st3.close();

                //Averiguar Materias Primas en bodega
                sql = "select * from MATERIAS_PRIMAS_PRODUCTO WHERE id_producto=" + nombreProducto;
                st3 = con.createStatement();
                rs = st3.executeQuery(sql);

                while (rs.next()) {

                    String id_materia = rs.getString("id_materia_prima");
                    int cantidad_unidades = rs.getInt("cantidad_unidades");

                    int numMateriasBodega = cantidadMateriasPrimasBodega(id_materia);
                    if (numMateriasBodega >= cantidad_unidades) {

                        int alcanzanMaterias = numMateriasBodega / cantidad_unidades;
                        numProductosPotencial = Math.min(alcanzanMaterias, numProductosPotencial);
                    }
                }

                st3.close();

                if (numProductosPotencial >= cantidad) {

                    //Reservar componentes
                    sql = "select * from COMPONENTES_PRODUCTO WHERE id_producto=" + nombreProducto;
                    st3 = con.createStatement();
                    rs = st3.executeQuery(sql);

                    while (rs.next()) {

                        String id_componente = rs.getString("id_componente");
                        int cantidad_unidades = rs.getInt("cantidad_unidades");
                        reservarComponenteBodega(id_componente, cantidad * cantidad_unidades);
                    }
                    st3.close();

                    // Reservar materias primas
                    sql = "select * from MATERIAS_PRIMAS_PRODUCTO WHERE id_producto=" + nombreProducto;
                    st3 = con.createStatement();
                    rs = st3.executeQuery(sql);

                    while (rs.next()) {

                        String id_materia = rs.getString("id_materia_prima");
                        int cantidad_unidades = rs.getInt("cantidad_unidades");
                        reservarMateriaPrimaBodega(id_materia, cantidad * cantidad_unidades);
                    }

                    st3.close();

                    //Falta fecha esperada
                    crearItemsReservadosPedido(nombreProducto, id_pedido, cantidad);
                    resp = "Se registro su pedido, la fecha esperada de entrega es ";
                } else {
                    //Poner el pedido en estad ESPERA
                    st3 = con.createStatement();
                    sql = "update PEDIDO_PRODUCTO set ESTADO='Espera' where id=" + id_pedido;
                    st3.executeUpdate(sql);
                    st3.close();

                    resp = "En estos momentos no contamos con los suministros suficientes para proceder"
                            + "con su pedido, vamos a poner su pedido en estado de espera.";
                }

            }
            cerrarConexion();
            return resp;
        } catch (Exception e) {
            e.printStackTrace();
            cerrarConexion();
            return "error";
        }
    }

    @POST
    @Path("/registrarPedidoProductosCliente")
    /**
     * Registrar que los productos del pedido ya se entregaron
     *
     * @param id_pedido
     */
    public void registrarEntregaPedidoProductosCliente(int id_pedido) throws Exception {

        abrirConexion();
        String query = "select cantidad_producto from PEDIDO_PRODUCTO WHERE ID=" + id_pedido;

        Statement st = con.createStatement();
        ResultSet rs = st.executeQuery(query);
        int cantidad = -1;
        if (rs.next()) {
            cantidad = rs.getInt("cantidad_producto");
        }
        if (cantidad == -1) {
            throw new Exception("No se encontró el pedido");
        }

        st.close();
        query = "select * from ITEM where ID_PEDIDO=" + id_pedido + " and ESTADO='Reservado'";
        st = con.createStatement();
        rs = st.executeQuery(query);
        int cantidadProductosProducidos = 0;
        while (rs.next()) {

            cantidadProductosProducidos++;
        }
        st.close();

        if (cantidadProductosProducidos < cantidad) {

            throw new Exception("No se han terminado de producir todos los productos");
        } else {
            st = con.createStatement();
            rs = st.executeQuery(query);

            while (rs.next()) {

                int id = rs.getInt("ID");
                String sql2 = "update ITEM set ESTADO='Entregado' where ID = " + id;

                Statement st2 = con.createStatement();
                st2.executeUpdate(sql2);
                st2.close();
            }

            st.close();
            
            query ="UPDATE PEDIDO_PRODUCTO SET ESTADO='Entregado' WHERE ID="+id_pedido;
            st = con.createStatement();
            st.executeUpdate(query);
            st.close();
        }
        cerrarConexion();
    }

    @POST
    @Path("/consultarProductos")
    public JSONArray consultarProductos(List lista) throws Exception {

        JSONArray jArray = new JSONArray();
        abrirConexion();
        LinkedHashMap lCriterio = (LinkedHashMap) lista.get(0);
        String criterio = lCriterio.toString();
        if (criterio.equalsIgnoreCase("Rango existencias inventario")) {

            LinkedHashMap lRango1 = (LinkedHashMap) lista.get(1);
            LinkedHashMap lRango2 = (LinkedHashMap) lista.get(2);
            int rango1 = (int) lRango1.get("rango 1");
            int rango2 = (int) lRango2.get("rango 2");

            String sql = "Select * from (Select Producto.Nombre as nombreProducto,count(*) as cantidadInventario "
                    + "from (Item inner join Producto on "
                    + "Producto.nombre=Item.nombreProducto)"
                    + "WHERE Item.Estado ='Bodega' GROUP BY Producto.nombre) where cantidadInventario>" + rango1
                    + " AND cantidadInventario<" + rango2;
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery(sql);

            while (rs.next()) {

                String nomProd = rs.getString("nombreProducto");

                sql = "Select * from ITEM where nombre_producto='" + nomProd + "'";

                Statement st2 = con.createStatement();
                ResultSet rs2 = st2.executeQuery(sql);

                while (rs2.next()) {
                    JSONObject jObject = new JSONObject();
                    jObject.put("id", rs2.getInt("id"));
                    jObject.put("ESTADO", rs2.getString("ESTADO"));
                    jObject.put("NOMBRE_PRODUCTO", rs2.getString("NOMBRE_PRODUCTO"));
                    jObject.put("ETAPA", rs2.getInt("ETAPA"));
                    jObject.put("ID_PEDIDO", rs2.getInt("ID_PEDIDO"));
                    jArray.add(jObject);
                }

                st2.close();
            }
            st.close();
        } else if (criterio.equalsIgnoreCase("Etapa produccion")) {

            LinkedHashMap lEtapa = (LinkedHashMap) lista.get(1);
            int num_etapa = (int) lEtapa.get("etapa");

            String sql = "select Producto.nombre as nombre, Etapa_De_Produccion.numero_secuencia as etapa"
                    + "from (Etapa_De_Produccion inner join Producto on"
                    + " Etapa_De_Produccion.nombre_producto= Producto.nombre) Order By Producto.Nombre,"
                    + "Etapa_De_Produccion.numero_secuencia";
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery(sql);

            String prodActual = "";
            int cont = 0;
            while (rs.next()) {
                String prod = rs.getString("nombre");
                int etapa = rs.getInt("etapa");
                if (!prod.equals(prodActual)) {
                    cont = 1;
                    prodActual = prod;
                }
                if (prod.equals(prodActual)) {
                    cont++;
                }
                if (cont == num_etapa) {

                    sql = "select * from Item where etapa=" + etapa;
                    Statement st2 = con.createStatement();
                    ResultSet rs2 = st2.executeQuery(sql);

                    while (rs2.next()) {

                        JSONObject jO = new JSONObject();
                        jO.put("id", rs2.getInt("id"));
                        jO.put("estado", rs2.getString("estado"));
                        jO.put("nombre_producto", rs2.getString("nombre_producto"));
                        jO.put("etapa", rs2.getString("etapa"));
                        jO.put("id_pedido", rs2.getString("id_pedido"));
                        jArray.add(jO);
                    }

                    st2.close();
                }
            }

            st.close();
        } else if (criterio.equalsIgnoreCase("Fecha solicitud")) {

            LinkedHashMap lFechaSolicitud = (LinkedHashMap) lista.get(1);
            String fechaS = lFechaSolicitud.get("fecha_solicitud").toString();

            String sql = "select * from PEDIDO_PRODUCTO where fecha_solicitud = "
                    + "TO_DATE('" + fechaS + "','dd-mm-yyyy')";
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery(sql);

            while (rs.next()) {

                int id_pedido = rs.getInt("id");
                sql = "select * from ITEM where ID_PEDIDO =" + id_pedido;
                Statement st2 = con.createStatement();
                ResultSet rs2 = st2.executeQuery(sql);

                while (rs2.next()) {
                    JSONObject jO = new JSONObject();
                    jO.put("id", rs2.getInt("id"));
                    jO.put("ESTADO", rs2.getString("ESTADO"));
                    jO.put("NOMBRE_PRODUCTO", rs2.getString("NOMBRE_PRODUCTO"));
                    jO.put("ETAPA", rs2.getInt("ETAPA"));
                    jO.put("ID_PEDIDO", rs2.getInt("ID_PEDIDO"));
                    jArray.add(jO);
                }
                st2.close();
            }

            st.close();
        } else if (criterio.equalsIgnoreCase("Fecha entrega")) {

            LinkedHashMap lFechaSolicitud = (LinkedHashMap) lista.get(1);
            String fechaS = lFechaSolicitud.get("fecha_entrega").toString();

            String sql = "select * from PEDIDO_PRODUCTO where fecha_entrega = "
                    + "TO_DATE('" + fechaS + "','dd-mm-yyyy')";
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery(sql);

            while (rs.next()) {

                int id_pedido = rs.getInt("id");
                sql = "select * from ITEM where ID_PEDIDO =" + id_pedido;
                Statement st2 = con.createStatement();
                ResultSet rs2 = st2.executeQuery(sql);

                while (rs2.next()) {
                    JSONObject jO = new JSONObject();
                    jO.put("id", rs2.getInt("id"));
                    jO.put("ESTADO", rs2.getString("ESTADO"));
                    jO.put("NOMBRE_PRODUCTO", rs2.getString("NOMBRE_PRODUCTO"));
                    jO.put("ETAPA", rs2.getInt("ETAPA"));
                    jO.put("ID_PEDIDO", rs2.getInt("ID_PEDIDO"));
                    jArray.add(jO);
                }
                st2.close();
            }

            st.close();
        }
        cerrarConexion();
        return jArray;
    }

    @POST
    @Path("/consultarMateriasPrimas")
    public JSONArray consultarMateriasPrimas(List lista) throws Exception {

        JSONArray jArray = new JSONArray();
        abrirConexion();
        LinkedHashMap lCriterio = (LinkedHashMap) lista.get(0);
        String criterio = lCriterio.toString();
        if (criterio.equalsIgnoreCase("Rango existencias inventario")) {

            LinkedHashMap lRango1 = (LinkedHashMap) lista.get(1);
            LinkedHashMap lRango2 = (LinkedHashMap) lista.get(2);
            int rango1 = (int) lRango1.get("rango 1");
            int rango2 = (int) lRango2.get("rango 2");

            String sql = "Select * from (Select Materia_Prima.Nombre as nombreMateria,count(*) as "
                    + "cantidadInventario "
                    + "from (Materia_Prima_Item inner join Materia_Prima on "
                    + "Materia_Prima.nombre=Materia_Prima_Item.materia)"
                    + "WHERE Materia_Prima_Item.Estado ='Bodega' "
                    + "GROUP BY Materia_Prima.nombre) where cantidadInventario>" + rango1
                    + " AND cantidadInventario<" + rango2;
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery(sql);

            while (rs.next()) {

                String materia = rs.getString("nombreMateria");

                sql = "Select * from Materia_Prima_Item where materia='" + materia + "'";

                Statement st2 = con.createStatement();
                ResultSet rs2 = st2.executeQuery(sql);

                while (rs2.next()) {
                    JSONObject jO = new JSONObject();
                    jO.put("id", rs2.getInt("id"));
                    jO.put("ESTADO", rs2.getString("ESTADO"));
                    jO.put("MATERIA", rs2.getString("MATERIA"));
                    jO.put("ID_PEDIDO", rs2.getInt("ID_PEDIDO"));

                    jArray.add(jO);
                }

                st2.close();
            }
            st.close();
        } else if (criterio.equalsIgnoreCase("fecha_solicitud")) {

            LinkedHashMap lFechaSolicitud = (LinkedHashMap) lista.get(1);
            String fechaS = lFechaSolicitud.get("fecha_solicitud").toString();

            String sql = "select * from PEDIDO_MATERIA_PRIMA where fecha_pedido = "
                    + "TO_DATE('" + fechaS + "','dd-mm-yyyy')";
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery(sql);

            while (rs.next()) {

                int id_pedido = rs.getInt("id");
                sql = "select * from MATERIA_PRIMA_ITEM where ID_PEDIDO =" + id_pedido;
                Statement st2 = con.createStatement();
                ResultSet rs2 = st2.executeQuery(sql);

                while (rs2.next()) {
                    JSONObject jO = new JSONObject();
                    jO.put("id", rs2.getInt("id"));
                    jO.put("ESTADO", rs2.getString("ESTADO"));
                    jO.put("MATERIA", rs2.getString("MATERIA"));
                    jO.put("ID_PEDIDO", rs2.getInt("ID_PEDIDO"));
                    jArray.add(jO);
                }
                st2.close();
            }

            st.close();
        } else if (criterio.equalsIgnoreCase("Fecha entrega")) {

            LinkedHashMap lFechaSolicitud = (LinkedHashMap) lista.get(1);
            String fechaS = lFechaSolicitud.get("fecha_entrega").toString();

            String sql = "select * from PEDIDO_MATERIA_PRIMA where fecha_entrega = "
                    + "TO_DATE('" + fechaS + "','dd-mm-yyyy')";
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery(sql);

            while (rs.next()) {

                int id_pedido = rs.getInt("id");
                sql = "select * from MATERIA_PRIMA_ITEM where ID_PEDIDO =" + id_pedido;
                Statement st2 = con.createStatement();
                ResultSet rs2 = st2.executeQuery(sql);

                while (rs2.next()) {
                    JSONObject jO = new JSONObject();
                    jO.put("id", rs2.getInt("id"));
                    jO.put("ESTADO", rs2.getString("ESTADO"));
                    jO.put("MATERIA", rs2.getString("MATERIA"));
                    jO.put("ID_PEDIDO", rs2.getInt("ID_PEDIDO"));
                    jArray.add(jO);
                }
                st2.close();
            }

            st.close();
        }else if ( criterio.equalsIgnoreCase("Tipo_material")){
            LinkedHashMap lTipo = (LinkedHashMap) lista.get(1);
            String tipo = lTipo.get("Tipo_material").toString();
            
            String sql = "select * from MATERIA_PRIMA where TIPO = '"+tipo+"'";
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery(sql);
            
            while ( rs.next()){
                
                String nombreMateria = rs.getString("nombre");
                sql = "select * from MATERIA_PRIMA_ITEM where materia = '"+nombreMateria+"'";
                Statement st2 = con.createStatement();
                ResultSet rs2 = st2.executeQuery(sql);
                
                while ( rs2.next()){
                    JSONObject jO = new JSONObject();
                    jO.put("id", rs2.getInt("id"));
                    jO.put("ESTADO", rs2.getString("ESTADO"));
                    jO.put("MATERIA", rs2.getString("MATERIA"));
                    jO.put("ID_PEDIDO", rs2.getInt("ID_PEDIDO"));
                    jArray.add(jO);
                    
                }
            }
        }
        cerrarConexion();
        return jArray;
    }

    @POST
    @Path("/consultarComponentes")
    public JSONArray consultarComponentes(List lista) throws Exception {

        JSONArray jArray = new JSONArray();
        abrirConexion();
        LinkedHashMap lCriterio = (LinkedHashMap) lista.get(0);
        String criterio = lCriterio.toString();
        if (criterio.equalsIgnoreCase("Rango existencias inventario")) {

            LinkedHashMap lRango1 = (LinkedHashMap) lista.get(1);
            LinkedHashMap lRango2 = (LinkedHashMap) lista.get(2);
            int rango1 = (int) lRango1.get("rango 1");
            int rango2 = (int) lRango2.get("rango 2");

            String sql = "Select * from (Select Componente.Nombre as nombreComponente,count(*) as "
                    + "cantidadInventario "
                    + "from (Componente_Item inner join Componente on "
                    + "Componente.nombre=Componente_Item.componente)"
                    + "WHERE Componente_Item.Estado ='Bodega' "
                    + "GROUP BY Componente.nombre) where cantidadInventario>" + rango1
                    + " AND cantidadInventario<" + rango2;
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery(sql);

            while (rs.next()) {

                String componente = rs.getString("nombreComponente");

                sql = "Select * from componente_item where componente='" + componente + "'";

                Statement st2 = con.createStatement();
                ResultSet rs2 = st2.executeQuery(sql);

                while (rs2.next()) {
                    JSONObject jO = new JSONObject();
                    jO.put("id", rs2.getInt("id"));
                    jO.put("ESTADO", rs2.getString("ESTADO"));
                    jO.put("componente", rs2.getString("componente"));
                    jO.put("ID_PEDIDO", rs2.getInt("ID_PEDIDO"));

                    jArray.add(jO);
                }

                st2.close();
            }
            st.close();
        } else if (criterio.equalsIgnoreCase("fecha_solicitud")) {

            LinkedHashMap lFechaSolicitud = (LinkedHashMap) lista.get(1);
            String fechaS = lFechaSolicitud.get("fecha_solicitud").toString();

            String sql = "select * from PEDIDO_COMPONENTE where fecha_pedido = "
                    + "TO_DATE('" + fechaS + "','dd-mm-yyyy')";
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery(sql);

            while (rs.next()) {

                int id_pedido = rs.getInt("id");
                sql = "select * from COMPONENTE_ITEM where ID_PEDIDO =" + id_pedido;
                Statement st2 = con.createStatement();
                ResultSet rs2 = st2.executeQuery(sql);

                while (rs2.next()) {
                    JSONObject jO = new JSONObject();
                    jO.put("id", rs2.getInt("id"));
                    jO.put("ESTADO", rs2.getString("ESTADO"));
                    jO.put("componente", rs2.getString("componente"));
                    jO.put("ID_PEDIDO", rs2.getInt("ID_PEDIDO"));
                    jArray.add(jO);
                }
                st2.close();
            }

            st.close();
        } else if (criterio.equalsIgnoreCase("Fecha entrega")) {

            LinkedHashMap lFechaSolicitud = (LinkedHashMap) lista.get(1);
            String fechaS = lFechaSolicitud.get("fecha_entrega").toString();

            String sql = "select * from PEDIDO_componente where fecha_entrega = "
                    + "TO_DATE('" + fechaS + "','dd-mm-yyyy')";
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery(sql);

            while (rs.next()) {

                int id_pedido = rs.getInt("id");
                sql = "select * from componente_ITEM where ID_PEDIDO =" + id_pedido;
                Statement st2 = con.createStatement();
                ResultSet rs2 = st2.executeQuery(sql);

                while (rs2.next()) {
                    JSONObject jO = new JSONObject();
                    jO.put("id", rs2.getInt("id"));
                    jO.put("ESTADO", rs2.getString("ESTADO"));
                    jO.put("componente", rs2.getString("componente"));
                    jO.put("ID_PEDIDO", rs2.getInt("ID_PEDIDO"));
                    jArray.add(jO);
                }
                st2.close();
            }

            st.close();
        }else if ( criterio.equalsIgnoreCase("Tipo_material")){
            LinkedHashMap lTipo = (LinkedHashMap) lista.get(1);
            String tipo = lTipo.get("Tipo_material").toString();
            
            String sql = "select * from componente where TIPO = '"+tipo+"'";
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery(sql);
            
            while ( rs.next()){
                
                String componente = rs.getString("nombre");
                sql = "select * from componente_ITEM where componente = '"+componente+"'";
                Statement st2 = con.createStatement();
                ResultSet rs2 = st2.executeQuery(sql);
                
                while ( rs2.next()){
                    JSONObject jO = new JSONObject();
                    jO.put("id", rs2.getInt("id"));
                    jO.put("ESTADO", rs2.getString("ESTADO"));
                    jO.put("componente", rs2.getString("componente"));
                    jO.put("ID_PEDIDO", rs2.getInt("ID_PEDIDO"));
                    jArray.add(jO);
                    
                }
            }
        }
        cerrarConexion();
        return jArray;
    }
    // -------------------------------------------------
    // Metodos Adicionales
    // -------------------------------------------------
    public void crearItemsReservadosPedido(String nombreProducto, int id_pedido, int cantidad) throws Exception {

        for (int i = 0; i < cantidad; i++) {
            String sql = "select max (id) as MAXIMO from ITEM";
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery(sql);
            int id_item = -1;
            if (rs.next()) {
                id_item = rs.getInt("MAXIMO") + 1;

                //Crear pedido nuevo
                sql = "INSERT INTO ITEM (id,ESTADO,NOMBRE_PRODUCTO,ID_PEDIDO)"
                        + " VALUES (" + id_item + ",'Pre Produccion','" + nombreProducto + "',"
                        + id_pedido + ")";

                Statement st2 = con.createStatement();

                st2.executeUpdate(sql);

                st2.close();
            }
            st.close();
        }

    }

    @POST
    @Path("/cantidadProductoEnBodega")
    public int cantidadProductoEnBodega(String nombre) throws Exception {
        System.out.println("Entrada parámetro cantidadProductoEnBodega");
        System.out.println(nombre);

        String query = "select count(*) as cuenta from ITEM where NOMBRE_PRODUCTO='" + nombre + "' and ESTADO='Bodega'";
        Statement st = con.createStatement();
        ResultSet rs = st.executeQuery(query);
        int resp = 0;
        if (rs.next()) {
            resp = rs.getInt("cuenta");
        }
        st.close();

        System.out.println("Return cantidadProductoEnBodega: " + resp);
        return resp;
    }

    @POST
    @Path("/cantidadMateriasPrimasBodega")
    public int cantidadMateriasPrimasBodega(String materia) throws Exception {
        System.out.println("Entrada parámetro cantidadMateriasPrimasBodega");
        System.out.println(materia);

        String query = "select count(*) as cuenta from MATERIA_PRIMA_ITEM "
                + "where MATERIA='" + materia + "' and ESTADO='Bodega'";
        Statement st = con.createStatement();
        ResultSet rs = st.executeQuery(query);
        int resp = 0;
        if (rs.next()) {
            resp = rs.getInt("cuenta");
        }
        st.close();

        System.out.println("Return cantidadMateriasPrimasBodega: " + resp);
        return resp;
    }

    @POST
    @Path("/cantidadComponentesBodega")
    public int cantidadComponentesBodega(String componente) throws Exception {
        System.out.println("Entrada parámetro cantidadComponentesBodega");
        System.out.println(componente);

        String query = "select count(*) as cuenta from COMPONENTE_ITEM "
                + "where COMPONENTE='" + componente + "' and ESTADO='Bodega'";
        Statement st = con.createStatement();
        ResultSet rs = st.executeQuery(query);
        int resp = 0;
        if (rs.next()) {
            resp = rs.getInt("cuenta");
        }
        st.close();

        System.out.println("Return cantidadComponentesBodega: " + resp);
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
     *
     * @param nombreProducto
     * @param cantidad
     * @param id_pedido
     * @throws Exception
     */
    public int reservarProductoBodega(String nombreProducto, int cantidad, int id_pedido) throws Exception {

        String query = "select * from ITEM where NOMBRE_PRODUCTO='" + nombreProducto + "' and ESTADO='Bodega'";
        Statement st = con.createStatement();
        ResultSet rs = st.executeQuery(query);

        int i;
        for (i = 0; i < cantidad && rs.next(); i++) {

            int id = rs.getInt("ID");
            String sql2 = "update ITEM set ESTADO='Reservado',ID_PEDIDO=" + id_pedido + " where ID = " + id;

            Statement st2 = con.createStatement();
            st2.executeUpdate(sql2);
            st2.close();
        }

        st.close();
        return i;
    }

    public int reservarComponenteBodega(String id_componente, int cantidad_unidades) throws Exception {

        String query = "select * from ComponenteItem where componente='" + id_componente
                + "' and ESTADO='Bodega'";
        Statement st = con.createStatement();
        ResultSet rs = st.executeQuery(query);

        int i;
        for (i = 0; i < cantidad_unidades && rs.next(); i++) {

            int id = rs.getInt("id");
            String sql2 = "update COMPONENTE_ITEM set ESTADO='Reservado' where id = " + id;

            Statement st2 = con.createStatement();
            st2.executeUpdate(sql2);
            st2.close();
        }

        st.close();
        return i;

    }

    public int reservarMateriaPrimaBodega(String id_materia, int cantidad_unidades) throws Exception {

        String query = "select * from MATERIAS_PRIMAS_ITEM where materia='" + id_materia
                + "' and ESTADO='Bodega'";
        Statement st = con.createStatement();
        ResultSet rs = st.executeQuery(query);

        int i;
        for (i = 0; i < cantidad_unidades && rs.next(); i++) {

            int id = rs.getInt("id");
            String sql2 = "update MATERIAS_PRIMAS_ITEM set ESTADO='Reservado' where id= " + id;

            Statement st2 = con.createStatement();
            st2.executeUpdate(sql2);
            st2.close();
        }

        st.close();
        return i;
    }

    //-------------------------------------------------------
    // Métodos para ensayar otros métodos
    //-------------------------------------------------------
    @POST
    @Path("/reservarProducto")
    public void reservarProductoREST(List lista) throws Exception {

        abrirConexion();
        LinkedHashMap lNombreProducto = (LinkedHashMap) lista.get(0);
        LinkedHashMap lCantidad = (LinkedHashMap) lista.get(1);
        LinkedHashMap lIdPedido = (LinkedHashMap) lista.get(2);

        reservarProductoBodega(lNombreProducto.get("nombreProducto").toString(), (int) lCantidad.get("cantidad"),
                (int) lIdPedido.get("id_pedido"));

        cerrarConexion();
    }

}
