/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package prodandes;

import java.util.LinkedHashMap;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.json.simple.JSONObject;

/**
 *
 * @author Jonathan
 */
@Path("/ServiciosMock")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ServiciosMock {

    @POST
    @Path("/registrarPedido")
    public JSONObject registrarPedido(JSONObject jO) throws Exception {

        System.out.println("jO " + jO.toJSONString());
        System.out.println("jO " + jO.toString());
        String nomProducto = jO.get("nombre").toString();
        int id_cliente = (int) jO.get("id_cliente");
        int cantidad = (int) jO.get("cantidad");
        String fecha = jO.get("fechaEsperada").toString();

        JSONObject jr = new JSONObject();
        jr.put("Respuesta", "Hola");
        return jr;
    }

    @POST
    @Path("/registrarEntregaPedidoProductosCliente")
    public void registrarEntregaPedidoProductosCliente(int id_pedido) {

        System.out.println(id_pedido);
    }
}
