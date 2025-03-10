/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2006 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
package org.tsm.model;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.logging.Level;

import org.compiere.model.MClient;
import org.compiere.model.MRequisition;
import org.compiere.model.MUser;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.ModelValidator;
import org.compiere.model.PO;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.TimeUtil;
import org.compiere.model.MCash;

/**
 *	Validator for company TSM
 *
 *  @author mfrojas
 */
public class ModelTSMUpdateCashBalance implements ModelValidator
{
	/**
	 *	Constructor.
	 *	The class is instantiated when logging in and client is selected/known
	 */
	public ModelTSMUpdateCashBalance ()
	{
		super ();
	}	//	MyValidator

	/**	Logger			*/
	private static CLogger log = CLogger.getCLogger(ModelTSMUpdateCashBalance.class);
	/** Client			*/
	private int		m_AD_Client_ID = -1;
	

	/**
	 *	Initialize Validation
	 *	@param engine validation engine
	 *	@param client client
	 */
	public void initialize (ModelValidationEngine engine, MClient client)
	{
		//client = null for global validator
		if (client != null) {
			m_AD_Client_ID = client.getAD_Client_ID();
			log.info(client.toString());
		}
		else  {
			log.info("Initializing global validator: "+this.toString());
		}

		//	Tables to be monitored
		//	Documents to be monitored
		engine.addDocValidate(MCash.Table_Name, this);
				

	}	//	initialize

    /**
     *	Model Change of a monitored Table.
     *	OFB Consulting Ltda. By mfrojas
     */
	public String modelChange (PO po, int type) throws Exception
	{
		log.info(po.get_TableName() + " Type: "+type);
		
	return null;
	}	//	modelChange
	
	public static String rtrim(String s, char c) {
	    int i = s.length()-1;
	    while (i >= 0 && s.charAt(i) == c)
	    {
	        i--;
	    }
	    return s.substring(0,i+1);
	}

	/**
	 *	Validate Document.
	 *	Called as first step of DocAction.prepareIt
     *	when you called addDocValidate for the table.
     *	Note that totals, etc. may not be correct.
	 *	@param po persistent object
	 *	@param timing see TIMING_ constants
     *	@return error message or null
	 */
	public String docValidate (PO po, int timing)
	{
		log.info(po.get_TableName() + " Timing: "+timing);

		if(timing == TIMING_AFTER_COMPLETE && po.get_Table_ID()==MCash.Table_ID)
		{
			MCash cash = (MCash)po;
			BigDecimal begbalance;
			begbalance = Env.ZERO;
			//sql que validar� el saldo inicial
			String sql = "SELECT coalesce(Endingbalance,0) as endingbalance FROM C_Cash where C_Cash_ID in " +
					" (SELECT max(C_Cash_ID) from C_Cash where C_Cashbook_ID = "+cash.getC_CashBook_ID()+" " +
							" and docstatus='CO' and c_Cash_id != ?)";
			
			log.config("sql "+sql);
			PreparedStatement pstmt = null;
			try
			{
				pstmt = DB.prepareStatement (sql,po.get_TrxName());
				pstmt.setInt (1, cash.get_ID());
				ResultSet rs = pstmt.executeQuery();
				if (rs.next ())
					begbalance = rs.getBigDecimal("Endingbalance");
				rs.close ();
				log.config("begbalance "+begbalance);
				pstmt.close ();
				pstmt = null;
			}
			catch (Exception e)
			{
				log.log(Level.SEVERE, sql, e);
			}
			
//			if(cash.getBeginningBalance().compareTo(begbalance)!=0)
//				return "El diario con nro documento "+cash.getDocumentNo()+" tiene saldo inicial err�neo";
			if(begbalance.compareTo(Env.ZERO)==0)
	            cash.setBeginningBalance(Env.ZERO);
	        else
	        {
	        	cash.setBeginningBalance(begbalance);
	        }
	    	
			cash.setEndingBalance(begbalance.add(cash.getStatementDifference()));

			cash.save();
			log.config("begbalance"+begbalance);
			//DB.executeUpdate("UPDATE C_CASH SET Beginningbalance="+begbalance+", endingbalance="+begbalance.add(cash.getStatementDifference())+" where c_Cash_id = "+cash.get_ID(),po.get_TrxName());
		}
		return null;
	}	//	docValidate

	/**
	 *	User Login.
	 *	Called when preferences are set
	 *	@param AD_Org_ID org
	 *	@param AD_Role_ID role
	 *	@param AD_User_ID user
	 *	@return error message or null
	 */
	public String login (int AD_Org_ID, int AD_Role_ID, int AD_User_ID)
	{
		log.info("AD_User_ID=" + AD_User_ID);

		return null;
	}	//	login


	/**
	 *	Get Client to be monitored
	 *	@return AD_Client_ID client
	 */
	public int getAD_Client_ID()
	{
		return m_AD_Client_ID;
	}	//	getAD_Client_ID


	/**
	 * 	String Representation
	 *	@return info
	 */
	public String toString ()
	{
		StringBuffer sb = new StringBuffer ("QSS_Validator");
		return sb.toString ();
	}	//	toString


	

}	