package com.simon_transporte.suite;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.openjpa.enhance.PCEnhancer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.simon_transporte.suite.db.helpers.ListResult;

import jakarta.jws.WebService;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.PersistenceUnit;
import jakarta.persistence.SynchronizationType;
import jakarta.persistence.Transient;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.xml.bind.JAXB;
import jakarta.xml.ws.WebServiceException;

public class WebserviceServet extends HttpServlet {

	protected Logger log = LoggerFactory.getLogger(getClass());

	private static final long serialVersionUID = -5479986178622823799L;

	private static final int PAGESIZE = 100;
	Map<String, Class<?>> wsServices = new HashMap<String, Class<?>>();

	@PersistenceUnit
	public EntityManagerFactory entityManagerFactory;

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
					throw new WebServiceException("Webservice name conflict '" + name + "' " + other.getCanonicalName()
							+ ", " + c.getCanonicalName());
				}

				if (c.getAnnotation(Entity.class) != null)
					PCEnhancer.main(new String[] { c.getCanonicalName() });

				try {
					wsServices.put(name, c);
				} catch (Exception e) {
					log.error(e.getMessage(), e);
					throw new WebServiceException(e);
				}
			}

		}

	}

	private Class<?> getClass(String className, String packageName) {
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

	@PersistenceContext
	protected <T> void doRW(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String qry = req.getQueryString();
		String path = req.getRequestURI();
		String servPath = Pattern.quote(req.getServletPath());
		resp.setHeader("Content-Type", "text/xml; charset=utf-8");
		path = path.replaceFirst("^" + servPath + "/", "");

		String[] pathParts = path.split("/");

		if (pathParts.length == 0) {
			resp.setStatus(400);
			resp.getOutputStream().write("Webservice not set\n".getBytes(StandardCharsets.UTF_8));
			return;
		}

		Class<?> ws = wsServices.get(pathParts[0]);

		// JAXBContext jaxbContext = JAXBContext.newInstance(ws);

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

			EntityManager entityManager = entityManagerFactory.createEntityManager(SynchronizationType.SYNCHRONIZED);
			entityManager.getTransaction().begin();

			try {
				Writer wrt = new OutputStreamWriter(resp.getOutputStream(), StandardCharsets.UTF_8);
				Object ent;
				switch (req.getMethod()) {
				case "GET":
					int page = 0;
					String pageS = req.getParameter("page");
					if (StringUtils.isNumeric(pageS)) {
						page = Integer.parseInt(pageS);
					}
					
					if (req.getParameter("list") != null) {
						List list = getAll(entityManager, ws, page, wrt);
						ListResult lr = list2ListResult(entityManager, list, ws, page);
						JAXB.marshal(lr, wrt);
						resp.setStatus(200);
					}else
					if (req.getParameter("example") != null) {
						String xml = req.getParameter("example");
						List list = findByExample(entityManager, JAXB.unmarshal(new StringReader(xml), ws), wrt,page,
								req.getParameter("or") != null);
						ListResult lr = list2ListResult(entityManager, list, ws, page);
						JAXB.marshal(lr, wrt);
					} else {
						ent = entityManager.find(ws, id); // load one specific by id
						if (ent == null) {
							resp.setStatus(404);
							wrt.write("not found\n");
							break;
						}
						resp.setStatus(200);
						JAXB.marshal(ent, wrt);

					}
					break;
				case "PUT":
					ent = JAXB.unmarshal(req.getReader(), ws);
					entityManager.persist(ent);
					resp.setStatus(200);
					entityManager.getTransaction().commit();
					JAXB.marshal(ent, wrt);
				}

				resp.setCharacterEncoding(StandardCharsets.UTF_8);

				if (resp.getStatus() >= 300) {
					resp.setHeader("Content-Type", "text/plain; charset=utf-8");
				}
				wrt.flush();
			} catch (Exception e) {
				resp.setHeader("Content-Type", "text/plain; charset=utf-8");
				resp.setStatus(500);
				log.error(e.getMessage(), e);
				throw new ServletException("Exception in WebserviceServet doRW");
			} finally {
				if (entityManager.getTransaction().isActive())
					entityManager.getTransaction().rollback();
				entityManager.close();
			}
		}

		return;
	}

	private <T> List<T> findByExample(EntityManager entityManager, T example, Writer wrt,int page,  boolean or) throws Exception {
		BeanInfo info = Introspector.getBeanInfo(example.getClass());

		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		@SuppressWarnings("unchecked")
		CriteriaQuery<T> crit = cb.createQuery((Class<T>) example.getClass());
		@SuppressWarnings("unchecked")
		Root<T> root = crit.from((Class<T>) example.getClass());

		List<Predicate> crits = new ArrayList<Predicate>();
		Field[] fields = example.getClass().getDeclaredFields();
		for (PropertyDescriptor pd : info.getPropertyDescriptors()) {
			Optional<Field> optField = Arrays.stream(fields).filter(f -> f.getName().equals(pd.getName())).findAny();
			if (!optField.isPresent())
				continue;
			Field field = optField.get();

			if (field.getAnnotation(Transient.class) != null) {
				continue;
			}

			if (pd.getReadMethod() == null) {
				continue;
			}
			if (pd.getWriteMethod() == null) {
				continue;
			}
			Object val = pd.getReadMethod().invoke(example);
			if (val == null) {
				continue;
			}

			crits.add(cb.equal(root.get(pd.getName()), val));
		}
		Predicate[] ps = crits.toArray(new Predicate[crits.size()]);

		Predicate finalP = or ? cb.or(ps) : cb.and(ps);
		TypedQuery<T> qry = entityManager.createQuery(crit.where(finalP));
		qry.setMaxResults(PAGESIZE);
		qry.setFirstResult(page * PAGESIZE);
		
		return qry.getResultList();

	}
	
	private static <T> ListResult list2ListResult(EntityManager entityManager, List<T> list,  Class<T> entityClass,  int page) {
		
		ListResult lr = new ListResult();
		lr.setType(entityClass.getSimpleName());
		lr.setCount(list.size());
		lr.setPage(page);

		for (T ent : list) {
			ListResult.ListEntry le = new ListResult.ListEntry();
			le.setToString(ent.toString());
			Object id = entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(ent);
			le.setId(id.toString());
			lr.getEntrys().add(le);
		}
		
		return lr;
	}

	private <T> List<T> getAll(EntityManager em, Class<T> entityClass, int page, Writer writer) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<T> cq = cb.createQuery(entityClass);
		Root<T> rootEntry = cq.from(entityClass);

		CriteriaQuery<T> all = cq.select(rootEntry);
		TypedQuery<T> allQuery = em.createQuery(all);
		allQuery.setMaxResults(PAGESIZE);
		allQuery.setFirstResult(page * PAGESIZE);
		return allQuery.getResultList();

	}

}
