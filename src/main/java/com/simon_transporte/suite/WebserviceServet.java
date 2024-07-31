package com.simon_transporte.suite;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.jws.WebService;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.PersistenceException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.xml.bind.JAXB;
import jakarta.xml.ws.WebServiceException;

public class WebserviceServet extends HttpServlet {

	private static final long serialVersionUID = -5479986178622823799L;
	Map<String, Class<?>> wsServices = new HashMap<String, Class<?>>();
	public EntityManager entityManager = null;

	@Override
	public void init() {
		String[] packs = getInitParameter("ws-package").split(",");

		for (String pack : packs) {
			InputStream stream = this.getClass().getClassLoader().getResourceAsStream(pack.replaceAll("[.]", "/"));
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

			List<Class<?>> cls = reader.lines().filter(line -> line.endsWith(".class")).map(c -> getClass(c, pack))
					.filter(c -> c.getAnnotation(WebService.class) != null).collect(Collectors.toList());
			try {
				reader.close();
			} catch (IOException e) {
				throw new WebServiceException(e);
			}

			for (Class<?> c : cls) {
				WebService anno = c.getAnnotation(WebService.class);
				String name = anno.name().length() > 0 ? anno.name() : c.getSimpleName();
				if (wsServices.containsKey(name)) {
					Class<?> other = wsServices.get(name).getClass();
					throw new WebServiceException("Webserice name conflict '" + name + "' " + other.getCanonicalName()
							+ " vs " + c.getCanonicalName());
				}

				try {
					wsServices.put(name, c);
				} catch (Exception e) {
					throw new WebServiceException(e);
				}
			}
		}
	}

	private Class getClass(String className, String packageName) {
		try {
			Class<?> cls = Class.forName(packageName + "." + className.substring(0, className.lastIndexOf('.')));
			return cls;
		} catch (ClassNotFoundException e) {
			// drop silent
		}
		return null;
	}

	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doRW(req, resp);
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doRW(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doRW(req, resp);
	}

	protected void doRW(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String qry = req.getQueryString();
		String path = req.getRequestURI();
		String servPath = Pattern.quote(req.getServletPath());

		path = path.replaceFirst("^" + servPath + "/", "");

		String[] pathParts = path.split("/");

		if (pathParts.length == 0) {
			resp.setStatus(400);
			resp.getOutputStream().write("Webservice not set\n".getBytes(StandardCharsets.UTF_8));
			return;
		}

		Class<?> ws = wsServices.get(pathParts[0]);

		if (ws == null) {
			resp.setStatus(404);
			resp.getOutputStream().write("Webservice not found\n".getBytes(StandardCharsets.UTF_8));
			return;
		}

		if (ws.getAnnotation(Entity.class) != null) {
			Field idField = null;
			for (Field field : ws.getDeclaredFields()) {
				if (field.getAnnotation(Id.class) != null) {
					idField = field;
					break;
				}
			}
			if (idField == null) {
				throw new PersistenceException("Id not found for entity class " + ws.getCanonicalName());
			}

			Object id = null;
			if (pathParts.length == 2) {
				if (idField.getType() == int.class || idField.getType() == Integer.class)
					id = Integer.parseInt(pathParts[1]);
				else if (idField.getType() == long.class || idField.getType() == Long.class)
					id = Long.parseLong(pathParts[1]);
				else if (idField.getType() == String.class)
					id = pathParts[1];
			}

			try {
				switch (req.getMethod()) {
				case "GET":
					Object ent = entityManager.getReference(ws, id);
					JAXB.marshal(ent, resp.getWriter());
					resp.setStatus(200);
					break;
				case "PUT":
					ent = JAXB.unmarshal(req.getReader(), ws);
					entityManager.persist(ent);
					resp.setStatus(200);
					JAXB.marshal(idField.get(ent), resp.getWriter());
				}	
			} catch (Exception e) {
				throw new ServletException(e);
			}
		}

		return;
	}

}
