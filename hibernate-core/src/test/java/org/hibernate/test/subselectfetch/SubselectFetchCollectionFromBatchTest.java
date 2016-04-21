/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2006-2011, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.test.subselectfetch;

import java.util.ArrayList;
import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.junit.Test;

import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.testing.junit4.BaseCoreFunctionalTestCase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Stephen Fikes
 * @author Gail Badner
 */
public class SubselectFetchCollectionFromBatchTest  extends BaseCoreFunctionalTestCase {

	@Override
	public void configure(Configuration cfg) {
		cfg.setProperty( Environment.GENERATE_STATISTICS, "true");
	}

	@Test
	public void testSubselectFetchFromEntityBatch() {
		Session s = openSession();
		Transaction t = s.beginTransaction();
		EmployeeGroup group1 = new EmployeeGroup();
		Employee employee1 = new Employee("Jane");
		Employee employee2 = new Employee("Jeff");
		group1.addEmployee(employee1);
		group1.addEmployee(employee2);
		EmployeeGroup group2 = new EmployeeGroup();
		Employee employee3 = new Employee("Joan");
		Employee employee4 = new Employee("John");
		group2.addEmployee(employee3);
		group2.addEmployee( employee4 );

		s.save( group1 );
		s.save( group2 );
		s.flush();

		s.clear();

		sessionFactory().getStatistics().clear();

		EmployeeGroup[] groups = new EmployeeGroup[] {
				(EmployeeGroup) s.load(EmployeeGroup.class, group1.getId()),
				(EmployeeGroup) s.load(EmployeeGroup.class, group2.getId())
		};

		// groups should only contain proxies
		assertEquals( 0, sessionFactory().getStatistics().getPrepareStatementCount() );

		for (EmployeeGroup group : groups) {
			assertFalse( Hibernate.isInitialized( group ) );
		}

		assertEquals( 0, sessionFactory().getStatistics().getPrepareStatementCount() );


		for (int i = 0 ; i < groups.length; i++ ) {
			// Both groups get initialized  and are added to the PersistenceContext when i == 0;
			// Still need to call Hibernate.initialize( groups[i] ) for i > 0 so that the entity
			// in the PersistenceContext gets assigned to its respective proxy target (is this a
			// bug???)
			Hibernate.initialize( groups[ i ] );
			assertTrue( Hibernate.isInitialized( groups[i] ) );
			// the collections should be uninitialized
			assertFalse( Hibernate.isInitialized( groups[i].getEmployees() ) );
		}

		// both Group proxies should have been loaded in the same batch;
		assertEquals( 1, sessionFactory().getStatistics().getPrepareStatementCount() );
		sessionFactory().getStatistics().clear();

		for (EmployeeGroup group : groups) {
			assertTrue( Hibernate.isInitialized( group ) );
			assertFalse( Hibernate.isInitialized( group.getEmployees() ) );
		}

		assertEquals( 0, sessionFactory().getStatistics().getPrepareStatementCount() );

		// now initialize the collection in the first; collections in both groups
		// should get initialized
		Hibernate.initialize( groups[0].getEmployees() );

		assertEquals( 1, sessionFactory().getStatistics().getPrepareStatementCount() );
		sessionFactory().getStatistics().clear();

		// all collections should be initialized now
		for (EmployeeGroup group : groups) {
			assertTrue( Hibernate.isInitialized( group.getEmployees() ) );
		}

		assertEquals( 0, sessionFactory().getStatistics().getPrepareStatementCount() );

		t.rollback();
		s.close();
	}

	@Test
	public void testSubselectFetchFromQueryList() {
		Session s = openSession();
		Transaction t = s.beginTransaction();
		EmployeeGroup group1 = new EmployeeGroup();
		Employee employee1 = new Employee("Jane");
		Employee employee2 = new Employee("Jeff");
		group1.addEmployee(employee1);
		group1.addEmployee(employee2);
		EmployeeGroup group2 = new EmployeeGroup();
		Employee employee3 = new Employee("Joan");
		Employee employee4 = new Employee("John");
		group2.addEmployee(employee3);
		group2.addEmployee( employee4 );

		s.save( group1 );
		s.save( group2 );
		s.flush();

		s.clear();

		sessionFactory().getStatistics().clear();

		List<EmployeeGroup> results = s.createQuery(
				"from SubselectFetchCollectionFromBatchTest$EmployeeGroup where id in :groups"
		).setParameterList(
				"groups",
				new Long[] { group1.getId(), group2.getId() }
		).list();

		assertEquals( 1, sessionFactory().getStatistics().getPrepareStatementCount() );
		sessionFactory().getStatistics().clear();

		for (EmployeeGroup group : results) {
			assertTrue( Hibernate.isInitialized( group ) );
			assertFalse( Hibernate.isInitialized( group.getEmployees() ) );
		}

		assertEquals( 0, sessionFactory().getStatistics().getPrepareStatementCount() );

		// now initialize the collection in the first; collections in both groups
		// should get initialized
		Hibernate.initialize( results.get( 0 ).getEmployees() );

		assertEquals( 1, sessionFactory().getStatistics().getPrepareStatementCount() );
		sessionFactory().getStatistics().clear();

		// all collections should be initialized now
		for (EmployeeGroup group : results) {
			assertTrue( Hibernate.isInitialized( group.getEmployees() ) );
		}

		assertEquals( 0, sessionFactory().getStatistics().getPrepareStatementCount() );

		t.rollback();
		s.close();
	}

	public Class<?>[] getAnnotatedClasses() {
		return new Class<?>[] { EmployeeGroup.class, Employee.class };
	}


	@Entity
	@Table(name = "EmployeeGroup")
	@BatchSize(size = 1000)
	private static class EmployeeGroup {
		@Id
		@GeneratedValue
		private Long id;

		@OneToMany(cascade = CascadeType.ALL)
		@Fetch(FetchMode.SUBSELECT)
		private List<Employee> employees = new ArrayList<Employee>();

		public EmployeeGroup(long id) {
			this.id = id;
		}

		@SuppressWarnings("unused")
		protected EmployeeGroup() {
		}

		public boolean addEmployee(Employee employee) {
			return employees.add(employee);
		}

		public List<Employee> getEmployees() {
			return employees;
		}

		public long getId() {
			return id;
		}

		@Override
		public String toString() {
			return id.toString();
		}
	}


	@Entity
	@Table(name = "Employee")
	private static class Employee {
		@Id
		@GeneratedValue
		private Long id;
		private String name;

		public String getName() {
			return name;
		}

		@SuppressWarnings("unused")
		private Employee() {
		}

		public Employee(String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return name;
		}
	}
}
