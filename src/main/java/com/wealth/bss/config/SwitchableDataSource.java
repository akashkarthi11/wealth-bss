package com.wealth.bss.config;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.AbstractDataSource;

import com.zaxxer.hikari.HikariDataSource;

// The datasource every query uses. It forwards to one connection pool; an admin's change builds a new pool and swaps it in here, so no other class
// has to know. Connections already handed out finish on the old pool, which is then closed.
public class SwitchableDataSource extends AbstractDataSource implements AutoCloseable {

	private volatile HikariDataSource delegate;

	public SwitchableDataSource(HikariDataSource first) {
		this.delegate = first;
	}

	@Override
	public Connection getConnection() throws SQLException {
		return delegate.getConnection();
	}

	@Override
	public Connection getConnection(String username, String password) throws SQLException {
		return delegate.getConnection(username, password);
	}

	// Uses the new pool from now on and closes the old one.
	public void swap(HikariDataSource next) {
		HikariDataSource old = delegate;
		delegate = next;
		old.close();
	}

	public DataSource current() {
		return delegate;
	}

	@Override
	public void close() {
		delegate.close();
	}
}
