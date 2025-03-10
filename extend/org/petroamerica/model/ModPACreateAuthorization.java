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
package org.petroamerica.model;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.adempiere.exceptions.DBException;
import org.compiere.model.MClient;
import org.compiere.model.MOrder;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.ModelValidator;
import org.compiere.model.PO;
import org.compiere.model.X_PA_Authorization;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
/**
 *	Validator for PA
 *
 *  @author Italo Ni�oles
 */
public class ModPACreateAuthorization implements ModelValidator
{
	/**
	 *	Constructor.
	 *	The class is instantiated when logging in and client is selected/known
	 */
	public ModPACreateAuthorization ()
	{
		super ();
	}	//	MyValidator

	/**	Logger			*/
	private static CLogger log = CLogger.getCLogger(ModPACreateAuthorization.class);
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
			log.info("Initializing Model Price Validator: "+this.toString());
		}
		//doc to be monitored
		//engine.addDocValidate(MInOut.Table_Name, this);
		engine.addDocValidate(MOrder.Table_Name, this);
		
	}	//	initialize

    /**
     *	Model Change of a monitored Table.
     *	OFB Consulting Ltda. By italo ni�oles
     */
	public String modelChange (PO po, int type) throws Exception
	{
		log.info(po.get_TableName() + " Type: "+type);
		
		return null;
	}	//	modelChange

	public String docValidate (PO po, int timing)
	{
		log.info(po.get_TableName() + " Timing: "+timing);		
		if(timing == TIMING_BEFORE_COMPLETE && po.get_Table_ID()==MOrder.Table_ID) 
		{	
			MOrder order = (MOrder)po;
			if(order.isSOTrx())
			{
				//se crea registro en autorizations
				X_PA_Authorization au = new X_PA_Authorization(po.getCtx(), 0, po.get_TrxName());
				String sql = "SELECT * FROM rvofb_authorization WHERE C_BPartner_ID = ?";
				PreparedStatement pstmt = null;
				ResultSet rs = null;
				try 
				{
					pstmt = DB.prepareStatement(sql, po.get_TrxName());
					pstmt.setInt(1, order.getC_BPartner_ID());
					rs = pstmt.executeQuery();
					if (rs.next())
					{
						au.setAD_Org_ID(order.getAD_Org_ID());
						au.setIsActive(true);
						au.set_CustomColumn("C_Order_ID", order.get_ID());
						au.setC_BPartner_ID(order.getC_BPartner_ID());
						au.setSalesRep_ID(rs.getInt("SalesRep_ID"));
						au.setAmount(rs.getBigDecimal("Amount"));
						au.setQty(rs.getBigDecimal("Qty"));
						au.setday(rs.getBigDecimal("day"));
						au.setDueAmt(rs.getBigDecimal("DueAmt"));
						au.setoverdraft(rs.getBigDecimal("overdraft"));
						au.setgiro_estimado(rs.getBigDecimal("giro_estimado"));
						au.setDescription(rs.getString("Description"));
						//nuevo campo pedido 28-08-2019
						au.set_CustomColumn("day2",rs.getBigDecimal("day2"));
						au.save(po.get_TrxName());
					}
					pstmt.close(); pstmt = null;
					rs.close(); rs = null;
				}catch (SQLException e)
				{
					throw new DBException(e, sql);
				}
			}
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
		StringBuffer sb = new StringBuffer ("ModelPrice");
		return sb.toString ();
	}	//	toString


	

}	
